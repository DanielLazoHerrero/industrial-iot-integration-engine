package com.industry.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Hilo concurrente (Java 1.4.2) para la lectura de básculas industriales via Socket TCP/IP.
 * Implementa el protocolo de comunicación hardware a bajo nivel y el registro
 * transaccional en la base de datos Oracle EBS.
 */
public class ScaleReader implements Runnable {

    private Connection connection;
    private String scaleIp;
    private int scalePort;
    
    // Parametrizacion de negocio
    private double maxWeightUne;
    private double maxWeightInternal;
    private double minWeightUne;
    private double minWeightInternal;
    
    // Trazabilidad
    private String machineId;
    private String shift;
    private String itemCode;
    private int inventoryItemId;

    private boolean keepRunning;

    public ScaleReader(Connection conn, String ip, String port, 
                       String maxUne, String maxInt, String minUne, String minInt, 
                       String machine, String shift, String item, int itemId) {
        this.connection = conn;
        this.scaleIp = ip;
        this.scalePort = Integer.valueOf(port).intValue();
        
        // Transformacion segura de comas a puntos para el parseo en Java 1.4
        this.maxWeightUne = Double.valueOf(maxUne.replace(',', '.')).doubleValue();
        this.maxWeightInternal = Double.valueOf(maxInt.replace(',', '.')).doubleValue();
        this.minWeightUne = Double.valueOf(minUne.replace(',', '.')).doubleValue();
        this.minWeightInternal = Double.valueOf(minInt.replace(',', '.')).doubleValue();
        
        this.machineId = machine;
        this.shift = shift;
        this.itemCode = item;
        this.inventoryItemId = itemId;
        this.keepRunning = true;
    }

    public void run() {
        Socket socket = null;
        BufferedReader reader = null;
        PrintWriter writer = null;
        
        // Procedimiento PL/SQL abstraido para insercion de logs de pesaje
        String sqlInsertWeight = "BEGIN PKG_ERP_INTEGRATION.INSERT_WEIGHT_LOG(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?); END;";
        int sequenceCounter = 0;

        try {
            // 1. Apertura de la conexion de red con el microcontrolador de la bascula
            socket = new Socket(scaleIp, scalePort);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // 2. Handshake y configuracion del hardware
            writer.println();
            writer.println("user admin\r\n");
            
            // Seteo de cuotas/umbrales directamente en la memoria de la bascula
            writer.println("w sp0111 " + maxWeightInternal + "\r\n");
            writer.println("w sp0112 " + minWeightInternal + "\r\n");
            writer.println("w sd0105 " + maxWeightUne + "\r\n");
            writer.println("w sd0205 " + minWeightUne + "\r\n");
            writer.println("callback wt0101\r\n");

            int charRead;
            StringBuffer weightBuffer = new StringBuffer();
            double lastValidWeight = 0.0;
            double currentWeight = 0.0;
            int weightDropCounter = 0;
            boolean isNewPallet = true;

            // 3. Bucle infinito de lectura manual de buffer (Procesamiento a bajo nivel)
            while (keepRunning) {
                charRead = reader.read();
                if (charRead == -1) break; // Fin del stream
                
                weightBuffer.append((char) charRead);

                // Detectamos final de trama (Line Feed ASCII 10)
                if (charRead == 10) {
                    String rawFrame = weightBuffer.toString();
                    weightBuffer.setLength(0); // Vaciamos buffer optimizando memoria
                    
                    int equalSignIndex = rawFrame.indexOf('=');
                    if (equalSignIndex > 0) {
                        String weightStr = rawFrame.substring(equalSignIndex + 1).trim();
                        
                        try {
                            currentWeight = Double.valueOf(weightStr.replace(',', '.')).doubleValue();
                            
                            // Logica de deteccion de peso estable (Filtro de ruido mecanico)
                            if (currentWeight > 20.0 && isNewPallet) { 
                                if (currentWeight > lastValidWeight) {
                                    lastValidWeight = currentWeight;
                                } else {
                                    weightDropCounter++;
                                }
                            } else if (currentWeight < 10.0 && !isNewPallet) {
                                isNewPallet = true; // La bascula se ha vaciado, lista para el siguiente
                            }
                        } catch (NumberFormatException nfe) {
                            // Ignoramos tramas corruptas generadas por interferencia de red
                        }

                        // Si el peso empieza a caer consistentemente, asumimos que el palet se ha retirado
                        if (weightDropCounter > 2) {
                            sequenceCounter++;
                            
                            // 4. Volcado transaccional a Oracle EBS
                            registerWeightInDatabase(sqlInsertWeight, sequenceCounter, lastValidWeight);
                            
                            // Reset del ciclo de pesaje
                            lastValidWeight = 0.0;
                            currentWeight = 0.0;
                            weightDropCounter = 0;
                            isNewPallet = false;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CRITICAL] Error en comunicacion con bascula TCP/IP: " + e.getMessage());
        } finally {
            // Clausura segura de recursos de red
            try { if (writer != null) writer.close(); } catch (Exception e) {}
            try { if (reader != null) reader.close(); } catch (Exception e) {}
            try { if (socket != null) socket.close(); } catch (IOException e) {}
        }
    }

    /**
     * Invoca el procedimiento almacenado de Oracle para persistir la lectura del hardware.
     */
    private void registerWeightInDatabase(String sql, int counter, double finalWeight) {
        CallableStatement stmt = null;
        try {
            stmt = connection.prepareCall(sql);
            stmt.setString(1, machineId);
            stmt.setString(2, shift);
            stmt.setString(3, itemCode);
            stmt.setInt(4, inventoryItemId);
            stmt.setInt(5, counter);
            stmt.setDouble(6, maxWeightUne);
            stmt.setDouble(7, maxWeightInternal);
            stmt.setDouble(8, minWeightUne);
            stmt.setDouble(9, minWeightInternal);
            stmt.setDouble(10, finalWeight);
            stmt.registerOutParameter(11, Types.VARCHAR);
            
            stmt.execute();
            
            String response = stmt.getString(11);
            if (response != null && response.trim().length() > 0) {
                System.out.println("[DB_WARNING] API Oracle MSG: " + response);
            }
        } catch (SQLException ex) {
            System.err.println("[DB_ERROR] Fallo al insertar pesaje " + finalWeight + "kg en BD: " + ex.getMessage());
        } finally {
            try { if (stmt != null) stmt.close(); } catch (SQLException se) {}
        }
    }
    
    public void stopEngine() {
        this.keepRunning = false;
    }
}