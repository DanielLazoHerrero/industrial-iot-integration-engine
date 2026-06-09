package com.industry.engine;

/**
 * Motor de interpolacion de variables para plantillas ZPL-II.
 * Busca delimitadores $#$TOKEN$#$ y los sustituye de forma eficiente (Java 1.4).
 */
public class TokenProcessor {

    public static String replaceTokens(String rawTemplate, String[] keys, String[] values) {
        if (rawTemplate == null) return "";
        
        String processedLine = rawTemplate;
        
        for (int i = 0; i < keys.length; i++) {
            String fullToken = "$#$" + keys[i] + "$#$";
            int startIndex = processedLine.indexOf(fullToken);
            
            if (startIndex != -1) {
                StringBuffer buffer = new StringBuffer();
                int lastIndex = 0;
                
                while (startIndex != -1) {
                    buffer.append(processedLine.substring(lastIndex, startIndex));
                    buffer.append(values[i] != null ? values[i] : "");
                    lastIndex = startIndex + fullToken.length();
                    startIndex = processedLine.indexOf(fullToken, lastIndex);
                }
                buffer.append(processedLine.substring(lastIndex));
                processedLine = buffer.toString();
            }
        }
        return processedLine;
    }
}