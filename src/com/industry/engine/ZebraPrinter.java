package com.industry.engine;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;

/**
 * Controlador de red para comunicacion binaria directa con hardware de impresion Zebra.
 */
public class ZebraPrinter {

    /**
     * Envia un payload ZPL directamente al puerto TCP raw de la impresora industrial.
     */
    public static boolean printLabel(String printerIp, int port, String zplPayload) {
        Socket socket = null;
        BufferedWriter writer = null;
        try {
            socket = new Socket(printerIp, port);
            
            // Escribimos directamente en el stream con la codificacion adecuada
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            
            // 1. Limpieza de buffer de impresora
            writer.write("\r\n");
            
            // 2. Conversion de caracteres especiales (IBM Code Page 850 para Zebra)
            String safePayload = convertToZebraAscii(zplPayload);
            
            writer.write(safePayload);
            writer.flush();
            return true;
        } catch (Exception e) {
            System.err.println("[PRINTER ERROR] Timeout o red caida enviando a " + printerIp);
            return false;
        } finally {
            try { if (writer != null) writer.close(); } catch (Exception e) {}
            try { if (socket != null) socket.close(); } catch (Exception e) {}
        }
    }
    
    /**
     * Mapea caracteres Unicode al estandar Code Page 850 / ISO soportado por las Zebra antiguas.
     */
    private static String convertToZebraAscii(String rawString) {
        char[] chars = rawString.toCharArray();
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < chars.length; i++) {
            if (chars[i] < 128) {
                sb.append(chars[i]);
            } else {
                switch (chars[i]) {
                    case 178: sb.append('\u00FD'); break; // '²'
                    case 193: sb.append('\u00B5'); break; // 'Á'
                    case 199: sb.append('\u0043'); break; // 'Ç' -> C
                    case 201: sb.append('\u0045'); break; // 'É' -> E
                    case 205: sb.append('\u00A1'); break; // 'Í'
                    case 209: sb.append('\u00A5'); break; // 'Ñ'
                    case 211: sb.append('\u00E0'); break; // 'Ó'
                    case 218: sb.append('\u00E9'); break; // 'Ú'
                    case 225: sb.append('\u00A0'); break; // 'á'
                    case 231: sb.append('\u0063'); break; // 'ç' -> c
                    case 233: sb.append('\u0065'); break; // 'é' -> e
                    case 237: sb.append('\u00A1'); break; // 'í'
                    case 241: sb.append('\u00A5'); break; // 'ñ' -> Ñ (estandar Zebra)
                    case 243: sb.append('\u00A2'); break; // 'ó'
                    case 250: sb.append('\u0075'); break; // 'ú' -> u
                    default:  sb.append(chars[i]);
                }
            }
        }
        return sb.toString();
    }
}