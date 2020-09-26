import util.Bigram;
import util.Type;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner sc = new Scanner(System.in);
        Bigram bigram = null;
        String input;
        System.out.println("\n\n\n\n\n");
        do {
            System.out.print("\nEnter Command (h - list of commands): ");
            input = sc.nextLine();
            switch (input) {
                case "train":
                    bigram = train(sc);
                    break;
                case "ti":
                    if (isTrained(bigram)) computeSentenceProbability_Input(sc, bigram);
                    break;
                case "tf":
                    if (isTrained(bigram)) computeSentenceProbability_File(sc, bigram);
                    break;
                case "p":
                    if (isTrained(bigram)) bigram.prettyPrintEverything2Files("./pretty-print/");
                    break;
                case "q":
                    System.exit(0);
                    break;
                case "h":
                    printHelp();
                    break;
            }
        } while(true);
    }

    private static boolean isTrained(Bigram bigram) throws InterruptedException {
        if (bigram == null) {
            System.err.println("\nTraining Required (use `train` command)");
            Thread.sleep(500);
            return false;
        } else {
            return true;
        }
    }

    private static Bigram train(Scanner sc) throws InterruptedException {
        boolean repeat;
        do {
            repeat = false;
            System.out.print("\nEnter File Name (default ./NLP6320_POSTaggedTrainingSet-Unix.txt): ");
            String fileName = sc.nextLine();
            if (fileName.isEmpty()) {
                fileName = "NLP6320_POSTaggedTrainingSet-Unix.txt";
            }

            try (BufferedReader brTest = new BufferedReader(new FileReader(fileName))) {
                brTest.close();
                System.out.println("Training Started");
                Bigram bigram = new Bigram("./NLP6320_POSTaggedTrainingSet-Unix.txt");
                System.out.println("Training Ended");
                return bigram;
            } catch (FileNotFoundException e) {
                System.err.println("\nFile Not Found");
                Thread.sleep(500);
                repeat = true;
            } catch (IOException e) {
                System.err.println("\nIOException (aborting back to main menu)");
                Thread.sleep(500);
            }
        } while(repeat);
        return null;
    }

    private static void computeSentenceProbability_File(Scanner sc, Bigram bigram) throws InterruptedException {
        boolean repeat;
        do {
            repeat = false;
            System.out.print("\nEnter File Name (default ./sentence.txt): ");
            String fileName = sc.nextLine();
            if (fileName.isEmpty()) {
                fileName = "./sentence.txt";
            }

            try (BufferedReader brTest = new BufferedReader(new FileReader(fileName))) {
                String sentence = brTest.readLine();
                System.out.println("\nSentence Got:\n" + sentence);

                Type smoothingType = getSmoothingType(sc);

                double prob = bigram.computeProbability(sentence, smoothingType);
                System.out.println("\nSentence Probability (including P(<s>)): " + prob);
            } catch (FileNotFoundException e) {
                System.err.println("\nFile Not Found");
                Thread.sleep(500);
                repeat = true;
            } catch (IOException e) {
                System.err.println("\nIOException (aborting back to main menu)");
                Thread.sleep(500);
            }
        } while(repeat);
    }

    private static void computeSentenceProbability_Input(Scanner sc, Bigram bigram) {
        System.out.print("\nEnter Test Sentence (default `chief_JJ asset_NN ._.`): ");
        String sentence = sc.nextLine();
        if (sentence.isEmpty()) {
            sentence = "chief_JJ asset_NN ._.";
        }
        System.out.println("\nSentence Got:\n" + sentence);

        Type smoothingType = getSmoothingType(sc);

        double prob = bigram.computeProbability(sentence, smoothingType);
        System.out.println("\nSentence Probability (including P(<s>)): " + prob);
    }

    private static Type getSmoothingType(Scanner sc) {
        int smoothingTypeInt;
        do {
            System.out.print(
                    "\nEnter Number (1 - NO SMOOTHING):" +
                            "\n             (2 - ADD ONE SMOOTHING):" +
                            "\n             (3 - GT DISCOUNT): ");
            smoothingTypeInt = sc.nextInt();
            sc.nextLine();
        } while (smoothingTypeInt < 1 || smoothingTypeInt > 3);

        Type smoothingType = Type.NO_SMOOTHING;
        switch (smoothingTypeInt) {
            case 1:
                smoothingType = Type.NO_SMOOTHING;
                break;
            case 2:
                smoothingType = Type.ADD_ONE_SMOOTHING;
                break;
            case 3:
                smoothingType = Type.GT_DISCOUNT;
                break;
        }

        return smoothingType;
    }

    private static void printHelp() {
        System.out.println(
                "  train - train bigram model\n" +
                "  ti - input sentence to compute probability (<s> & </s> added automatically)\n" +
                "  tf - input file to compute probability of sentence in the first line of file\n" +
                "  p - save 6 files into pretty-print directory (WARNING TAKES ~1-2 minutes AND REQUIRES ~7GB of file-space)\n" +
                "      files saved:\n" +
                "       - 0-token-to-index.txt\n" +
                "       - 1-bigram-counts.txt\n" +
                "       - 2-unigram-counts.txt\n" +
                "       - 3-prob-no-smoothing.txt\n" +
                "       - 4-prob-add-one-smoothing.txt\n" +
                "       - 5-prob-gt-discount.txt\n" +
                "  h - help menu\n" +
                "  q - quit");
    }
}
