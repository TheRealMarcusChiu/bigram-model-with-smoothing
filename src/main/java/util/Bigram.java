package util;

import lombok.Data;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Data
public class Bigram {

    public static final String SOS_MARKER = "<s>";
    public static final String EOS_MARKER = "</s>";
    public static final String SOS_SENTENCE = SOS_MARKER + "_" + SOS_MARKER + " ";
    public static final String EOS_SENTENCE = " " + EOS_MARKER + "_" + EOS_MARKER;

    ArrayList<String> tokenSetList;
    int tokenSetSize;
    public int[] unigramCounts;
    public int[][] bigramCounts;
    public double[] unigramProb_noSmoothing;
    public double[][] condProb_noSmoothing;
    public double[][] condProb_addOneSmoothing;
    public double[][] condProb_gtDiscount;

    public Bigram(String fileName) {
        setTokenSetList(fileName);
        setBigramCounts(fileName);
        computeUnigramProb_noSmoothing();
        computeCondProb_noSmoothingAndAddOneSmoothing();
        computeCondProb_gtDiscount();
    }

    public double computeProbability(String sentence, Type smoothingType) {
        String[] tokens = Tokenizer.tokenize(SOS_SENTENCE + sentence + EOS_SENTENCE);
        double prob = unigramProb_noSmoothing[0]; // probability of unigram P(SOS)
        for (int i = 1; i < tokens.length; i++) {
            int word1Index = tokenSetList.indexOf(tokens[i-1]);
            int word2Index = tokenSetList.indexOf(tokens[i]);
            if (word1Index == -1 || word2Index == -1) {
                System.out.println("sentence contains unseen word. outputting prob 0 because unigrams are not smoothed");
                return 0d;
            } else {
                switch (smoothingType) {
                    case NO_SMOOTHING:
                        prob *= condProb_noSmoothing[word1Index][word2Index];
                        break;
                    case ADD_ONE_SMOOTHING:
                        prob *= condProb_addOneSmoothing[word1Index][word2Index];
                        break;
                    case GT_DISCOUNT:
                        prob *= condProb_gtDiscount[word1Index][word2Index];
                        break;
                }
            }
        }
        return prob;
    }

    private void setTokenSetList(String fileName) {
        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            Set<String> tokenSet = new HashSet<>();
            stream.forEach(sentence -> tokenSet.addAll(Arrays.asList(Tokenizer.tokenize(sentence))));
            tokenSetList = new ArrayList<>(tokenSet);
            Collections.sort(tokenSetList);
            tokenSetList.add(0, EOS_MARKER); // add to front of list
            tokenSetList.add(0, SOS_MARKER); // add to front of list
            tokenSetSize = tokenSetList.size();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setBigramCounts(String fileName) {
        // initialize unigramCounts to 0
        unigramCounts = new int[tokenSetSize];

        // initialize bigramCounts to 0
        bigramCounts = new int[tokenSetSize][];
        for (int i = 0; i < tokenSetSize; i++) {
            bigramCounts[i] = new int[tokenSetSize];
        }

        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream.forEach(str -> {
                String[] tokens = Tokenizer.tokenize( SOS_SENTENCE + str + EOS_SENTENCE);
                for (int i = 1; i < tokens.length; i++) {
                    int wordIndex1 = tokenSetList.indexOf(tokens[i-1]);
                    int wordIndex2 = tokenSetList.indexOf(tokens[i]);
                    bigramCounts[wordIndex1][wordIndex2]++;
                    unigramCounts[wordIndex2]++;
                }
                unigramCounts[0]++;
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void computeUnigramProb_noSmoothing() {
        unigramProb_noSmoothing = new double[tokenSetSize];
        double numUnigrams = 0;
        for (int i = 0; i < tokenSetSize; i++) {
            numUnigrams += unigramCounts[i];
        }
        for (int i = 0; i < tokenSetSize; i++) {
            unigramProb_noSmoothing[i] = (double)unigramCounts[i] / numUnigrams;
        }
    }

    private void computeCondProb_noSmoothingAndAddOneSmoothing() {
        condProb_noSmoothing = generateCondProb(tokenSetSize);
        condProb_addOneSmoothing = generateCondProb(tokenSetSize);

        double normalizationFactor = 0;

        // calculate P(SOS|WORD) probabilities
        for (int j = 2; j < tokenSetSize; j++) {
            normalizationFactor += bigramCounts[0][j];
        }
        for (int j = 2; j < tokenSetSize; j++) {
            condProb_noSmoothing[0][j] = (double)bigramCounts[0][j] / normalizationFactor;
            condProb_addOneSmoothing[0][j] = (double)(bigramCounts[0][j] + 1) / (normalizationFactor + tokenSetSize - 2);
        }

        // calculate P(NON-SOS-OR-NON-EOS|WORD) probabilities
        for (int i = 2; i < tokenSetSize; i++) {
            normalizationFactor = 0;
            for (int j = 1; j < tokenSetSize; j++) {
                normalizationFactor += bigramCounts[i][j];
            }
            for (int j = 1; j < tokenSetSize; j++) {
                condProb_noSmoothing[i][j] = (double)bigramCounts[i][j] / normalizationFactor;
                condProb_addOneSmoothing[i][j] = (double)(bigramCounts[i][j] + 1) / (normalizationFactor + tokenSetSize - 1);
            }
        }

        setInvalidProbabilities(condProb_noSmoothing, tokenSetSize);
        setInvalidProbabilities(condProb_addOneSmoothing, tokenSetSize);
    }

    private void computeCondProb_gtDiscount() {
        condProb_gtDiscount = generateCondProb(tokenSetSize);

        int max = -1;
        double numBigrams = 0;
        for (int i = 0; i < tokenSetSize; i++) {
            for (int j = 0; j < tokenSetSize; j++) {
                int count = bigramCounts[i][j];
                numBigrams += count;
                if (count > max) {
                    max = count;
                }
            }
        }

        // initialize bucket
        setInvalidCounts(bigramCounts, tokenSetSize);
        double[] bucket = new double[max+2];
        for (int i = 0; i < tokenSetSize; i++) {
            for (int j = 0; j < tokenSetSize; j++) {
                Integer count = bigramCounts[i][j];
                if (!count.equals(-1)) {
                    bucket[count]++;
                }
            }
        }

        for (int i = 0; i < tokenSetSize; i++) {
            for (int j = 1; j < tokenSetSize; j++) {
                Integer c = bigramCounts[i][j];
                if (!c.equals(-1)) {
                    double jointProb = (((double)c + 1d) * bucket[c+1]) / (numBigrams * bucket[c]);
                    condProb_gtDiscount[i][j] = jointProb / unigramProb_noSmoothing[i];
                }
            }
        }

        setInvalidProbabilities(condProb_gtDiscount, tokenSetSize);
    }

    private static double[][] generateCondProb(int tokenSetSize) {
        double[][] condProb = new double[tokenSetSize][];
        for (int i = 0; i < tokenSetSize; i++) {
            condProb[i] = new double[tokenSetSize];
        }
        return condProb;
    }

    private static void setInvalidCounts(int[][] bigramCounts, int tokenSetSize) {
        // set invalid word sequences to -1
        for (int i = 0; i < tokenSetSize; i++) {
            bigramCounts[i][0] = -1;
        }
        for (int j = 0; j < tokenSetSize; j++) {
            bigramCounts[1][j] = -1;
        }
        bigramCounts[0][1] = -1;
    }

    private static void setInvalidProbabilities(double[][] condProb, int tokenSetSize) {
        // set invalid word sequences to -1
        for (int i = 0; i < tokenSetSize; i++) {
            condProb[i][0] = -1;
        }
        for (int j = 0; j < tokenSetSize; j++) {
            condProb[1][j] = -1;
        }
        condProb[0][1] = -1;
    }

    // WARNING THIS METHOD TAKES ~1-2 minutes AND REQUIRES ~7GB of file-space
    public void prettyPrintEverything2Files(String directory) throws IOException {
        // WRITE TOKEN-TO-INDEX
        BufferedWriter tokenToIndexWriter = new BufferedWriter(new FileWriter(directory + "0-token-to-index.txt"));
        StringBuilder builder = new StringBuilder();
        builder.append("token = index\n");
        for (int i = 0; i < tokenSetSize; i++) {
            builder.append(tokenSetList.get(i)).append(" = ").append(i).append("\n");
        }
        tokenToIndexWriter.write(builder.toString());
        tokenToIndexWriter.flush();
        tokenToIndexWriter.close();
        System.out.println("FINISHED WRITING 0-token-to-index.txt");

        // WRITE UNIGRAM COUNTS
        BufferedWriter unigramCountsWriter = new BufferedWriter(new FileWriter(directory + "1-unigram-counts.txt"));
        builder.setLength(0); // clear/empty buffer
        unigramCountsWriter.write("index = unigram-count\n");
        for (int i = 0; i < tokenSetSize; i++) {
            builder.append(i).append(" = ").append(unigramCounts[i]).append("\n");
        }
        unigramCountsWriter.write(builder.toString());
        unigramCountsWriter.close();
        System.out.println("FINISHED WRITING 1-unigram-counts.txt");

        // WRITE BIGRAM COUNTS
        BufferedWriter bigramCountsWriter = new BufferedWriter(new FileWriter(directory + "2-bigram-counts.txt"));
        builder.setLength(0); // clear/empty buffer
        builder.append("index-1 index-2 = bigram-count (-1 means invalid)\n");
        for (int i = 0; i < tokenSetSize; i++) {
            for (int j = 0; j < tokenSetSize; j++) {
                builder.append("\n").append(i).append(" ").append(j).append(" = ").append(bigramCounts[i][j]);
            }
            bigramCountsWriter.write(builder.toString());
            builder.setLength(0); // clear/empty buffer
        }
        bigramCountsWriter.close();
        System.out.println("FINISHED WRITING 2-bigram-counts.txt");

        // WRITE PROB NO SMOOTHING
        BufferedWriter condProb_noSmoothingWriter = new BufferedWriter(new FileWriter(directory + "3-conditional-prob-no-smoothing.txt"));
        builder.setLength(0); // clear/empty buffer
        builder.append("P(index-2|index-1) = probability\n");
        for (int i = 0; i < tokenSetSize; i++) {
            for (int j = 0; j < tokenSetSize; j++) {
                builder.append("\nP(").append(j).append("|").append(i).append(") = ").append(condProb_noSmoothing[i][j]);
            }
            condProb_noSmoothingWriter.write(builder.toString());
            builder.setLength(0); // clear/empty buffer
        }
        condProb_noSmoothingWriter.close();
        System.out.println("FINISHED WRITING 3-conditional-prob-no-smoothing.txt");

        // WRITE PROB ADD ONE SMOOTHING
        BufferedWriter condProb_addOneSmoothingWriter = new BufferedWriter(new FileWriter(directory + "4-conditional-prob-add-one-smoothing.txt"));
        builder.setLength(0); // clear/empty buffer
        condProb_addOneSmoothingWriter.write("P(index-2|index-1) = probability\n");
        for (int i = 0; i < tokenSetSize; i++) {
            for (int j = 0; j < tokenSetSize; j++) {
                condProb_addOneSmoothingWriter.write("\nP(" + j + "|" + i + ") = " + condProb_addOneSmoothing[i][j]);
            }
            condProb_addOneSmoothingWriter.write(builder.toString());
            builder.setLength(0); // clear/empty buffer
        }
        condProb_addOneSmoothingWriter.close();
        System.out.println("FINISHED WRITING 4-conditional-prob-add-one-smoothing.txt");

        // WRITE PROB GT DISCOUNT
        BufferedWriter condProb_gtDiscountWriter = new BufferedWriter(new FileWriter(directory + "5-conditional-prob-gt-discount.txt"));
        builder.setLength(0); // clear/empty buffer
        builder.append("P(index-2|index-1) = probability\n");
        for (int i = 0; i < tokenSetSize; i++) {
            for (int j = 0; j < tokenSetSize; j++) {
                builder.append("\nP(").append(j).append("|").append(i).append(") = ").append(condProb_gtDiscount[i][j]);
            }
            condProb_gtDiscountWriter.write(builder.toString());
            builder.setLength(0); // clear/empty buffer
        }
        condProb_gtDiscountWriter.close();
        System.out.println("FINISHED WRITING 5-conditional-prob-gt-discount.txt");
        System.out.println("if files are not shown, then possibly need to exit program");
    }
}
