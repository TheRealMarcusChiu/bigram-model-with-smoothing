package util;

public class Tokenizer {
    public static String[] tokenize(String sentence) {
        sentence = sentence.toLowerCase();
        String[] tokens = sentence.split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            int index = token.lastIndexOf("_");
            tokens[i] = token.substring(0, index);
        }
        return tokens;
    }
}
