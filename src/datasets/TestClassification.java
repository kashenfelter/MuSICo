package datasets;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import minhash.LocalitySensitiveHashing;
import utils.misc.Pair;

public class TestClassification {

	public static int knn = 5;
	public static int signature = 400;
	public static int bands = 50;
	public static boolean SemEvalAsymmetrical = true;
	private static boolean separateDirection = false;
	private static int trainInstances = 0;
	private static int testInstances = 0;
	private static LocalitySensitiveHashing dataIndex;
	private static LocalitySensitiveHashing directionIndex;
	
	public static void readTrainData(String file) throws Exception {
		readTrainData(file, -1);
	}

	public static void readTrainData(String file, int number) throws Exception {
		File dbfile = new File("MapDB/mapdb-relations-index");
		File dbfile2 = new File("MapDB/mapdb-directions-index");
		dbfile.deleteOnExit();
		dbfile2.deleteOnExit();
		dataIndex = new LocalitySensitiveHashing(dbfile, signature, bands);
		directionIndex = new LocalitySensitiveHashing(dbfile2, signature, bands);
		LineNumberReader lnr = new LineNumberReader(new FileReader(new File(file)));
		lnr.skip(Long.MAX_VALUE);
		int num_lines = lnr.getLineNumber();
		BufferedReader input = new BufferedReader(new FileReader(file));
		String aux;
		int num = 0;
		long startTime = System.nanoTime();
		while ((aux = input.readLine()) != null) {
			if (num % 1000 == 0)
			    System.out.println(String.valueOf(num) + "/" + String.valueOf(num_lines));
			HashSet<String> set = new HashSet<String>();
			for (String element : aux.substring(aux.indexOf(" ") + 1).trim().split(" ")) set.add(element);
			String cl = aux.substring(0, aux.indexOf(" "));
			if (cl.indexOf("(") != -1 && separateDirection) {
				String dr = cl.substring(cl.indexOf("("));
				cl = cl.substring(0, cl.indexOf("("));
				directionIndex.index(directionIndex.indexSize(),set.toArray(new String[0]), dr);
			}
			dataIndex.index(dataIndex.indexSize(), set.toArray(new String[0]),cl);
			num++;
			if (number > 0 && num > number) break;
			if (num % 10000 == 0) dataIndex.commitChanges();
		}
		long stopTime = System.nanoTime();
		long elapsedTime = stopTime - startTime;
		System.out.println("Indexing time: " + TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.NANOSECONDS));
		dataIndex.commitChanges();
		// dataIndex.computeValidity(knn);
		input.close();
	}

	public static LinkedList<Pair<String, String>> evaluateTestData(String file) throws Exception {
		return evaluateTestData(file, -1);
	}

	public static LinkedList<Pair<String, String>> evaluateTestData( String file, int number) throws Exception {
		if (number > 0) readTrainData(file, number);
		LineNumberReader lnr = new LineNumberReader(new FileReader(new File(file)));
		lnr.skip(Long.MAX_VALUE);
		int num_lines = lnr.getLineNumber();
		int num = 0;
		BufferedReader input = new BufferedReader(new FileReader(file));
		String aux = null;
		LinkedList<Pair<String, String>> results = new LinkedList<Pair<String, String>>();
		long startTime = System.nanoTime();
		while ((aux = input.readLine()) != null) {
			if (num % 1000 == 0)
				System.out.println(String.valueOf(num) + "/" + String.valueOf(num_lines));
			if (number-- > 0) continue;
			HashSet<String> set = new HashSet<String>();
			for (String element : aux.substring(aux.indexOf(" ") + 1).trim().split(" ")) set.add(element);
			String cl = aux.substring(0, aux.indexOf(" "));
			String clResult = dataIndex.queryNearest(set.toArray(new String[0]), knn, null).mostFrequent();
			if (clResult != null && separateDirection)
				clResult += directionIndex.queryNearest(set.toArray(new String[0]), knn).mostFrequent();
			Pair<String, String> p = new Pair<String, String>(cl, clResult);
			results.add(p);
			num++;
		}
		long stopTime = System.nanoTime();
		long elapsedTime = stopTime - startTime;
		System.out.println("Classification time: " + TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.NANOSECONDS));
		input.close();
		return results;
	}

	public static double[] evaluateResults(LinkedList<Pair<String, String>> results, String class_relation) {

		double numInstancesOfClass = 0;
		double numCorrectClassified = 0;
		double numClassified = 0;
		double numCorrect = 0;

		// pair.getFirst() has the true label
		// pair.getSecond() has the predicted label

		for (Pair<String, String> pair : results) {
			if (pair.getSecond() == null)
				pair.setSecond("UNKNOWN");
			String first = pair.getFirst();
			String second = pair.getSecond();
			if (first.equalsIgnoreCase(class_relation)) {
				numInstancesOfClass++;
				if (first.equalsIgnoreCase(second)) numCorrectClassified++;
			}			
			if (second.equalsIgnoreCase(class_relation)) numClassified++;
			if (first.equalsIgnoreCase(second)) numCorrect++;
		}

		double precision = numClassified == 0 ? 1.0 : (numCorrectClassified / numClassified);
		double recall = numInstancesOfClass == 0 ? 1.0 : (numCorrectClassified / numInstancesOfClass);
		double f1 = precision == 0 && recall == 0 ? 0.0 : (2.0 * ((precision * recall) / (precision + recall)));

		System.out.println();
		System.out.println("Results for class \t" + class_relation + "\t" + (dataIndex.indexSize(class_relation) + (int) numInstancesOfClass));
		System.out.println("Number of training instances : " + dataIndex.indexSize(class_relation));
		System.out.println("Number of test instances     : " + numInstancesOfClass);
		System.out.println("Number of classifications    : " + numClassified);
		System.out.println("Precision : " + precision);
		System.out.println("Recall    : " + recall);
		System.out.println("F1        : " + f1);

		trainInstances += dataIndex.indexSize(class_relation);
		testInstances += numInstancesOfClass;

		double accuracy = numCorrect / (float) results.size();

		return new double[] { accuracy, precision, recall, f1 };
	}

	public static void testSemEval(String train_data, String test_data) throws Exception {

		System.out.println("\nTest classification on SemEval...");

		System.out.println("Reading train data SemEval...");
		if (train_data==null)
			train_data="shingles/train-data-semeval.txt";
		readTrainData(train_data);

		System.out.println("Reading test data SemEval...");
		if (test_data==null)
			test_data="shingles/test-data-semeval.txt";

		LinkedList<Pair<String, String>> all_results = evaluateTestData(test_data);

		double[] results = { 0.0, 0.0, 0.0, 0.0 };

		String[] classes_asymmetrical = { "Cause-Effect(e1,e2)",
				"Cause-Effect(e2,e1)", "Component-Whole(e1,e2)",
				"Component-Whole(e2,e1)", "Content-Container(e1,e2)",
				"Content-Container(e2,e1)", "Entity-Destination(e1,e2)",
				"Entity-Destination(e2,e1)", "Entity-Origin(e1,e2)",
				"Entity-Origin(e2,e1)", "Instrument-Agency(e1,e2)",
				"Instrument-Agency(e2,e1)", "Member-Collection(e1,e2)",
				"Member-Collection(e2,e1)", "Message-Topic(e1,e2)",
				"Message-Topic(e2,e1)", "Product-Producer(e1,e2)",
				"Product-Producer(e2,e1)" };

		String[] classes_symmetrical = { "Cause-Effect", "Component-Whole",
				"Content-Container", "Entity-Destination", "Entity-Origin",
				"Instrument-Agency", "Member-Collection", "Message-Topic",
				"Product-Producer", "Other" };

		String[] classes;

		if (SemEvalAsymmetrical)
			classes = classes_asymmetrical;
		else
			classes = classes_symmetrical;

		for (String c : classes) {
			System.out.println();
			double[] results_aux = evaluateResults(all_results, c);
			for (int j = 1; j < results_aux.length; j++)
				results[j] = results[j] + results_aux[j];
			results[0] = results_aux[0];
		}
		
		for (int i = 1; i < results.length; i++) results[i] = results[i] / classes.length;
		System.out.println();
		System.out.println("Total train instances : " + trainInstances);
		System.out.println("Total test instances : " + testInstances);
		System.out.println("Macro-Average results for all classes...");
		System.out.println("Accuracy : " + results[0]);
		System.out.println("Precision : " + results[1]);
		System.out.println("Recall : " + results[2]);
		System.out.println("F1 : " + (2.0 * ((results[1] * results[2]) / (results[1] + results[2]))));
	}

	public static void testWikiEN() throws Exception {

		System.out.println();
		System.out.println("Test classification on English Wikipedia...");
		System.out.println("Reading train data WikiEN...");
		readTrainData("shingles/train-data-wikien.txt");
		System.out.println("Reading test data WikiEN...");
		LinkedList<Pair<String, String>> aux = evaluateTestData("shingles/test-data-wikien.txt");
		double[] resultsWiki = { 0.0, 0.0, 0.0, 0.0 };

		// All except classes with no test instances
		String[] classesWikiEn = { "job_title", "visited", "birth_place",
				"associate", "birth_year", "member_of", "birth_day", "opus",
				"death_year", "death_day", "education", "nationality",
				"executive", "employer", "death_place", "award", "father",
				"participant", "brother", "son", "associate_competition",
				"wife", "superior", "mother", "political_affiliation",
				"friend", "founder", "daughter", "husband", "religion",
				"influence", "underling", "sister", "grandfather", "ancestor",
				"grandson", "cousin", "role", "nephew", "granddaughter",
				"owns", "great_grandson", "aunt", "supported_idea",
				"great_grandfather", "brother_in_law" };

		for (String c : classesWikiEn) {
			System.out.println();
			double[] results_aux = evaluateResults(aux, c);
			for (int j = 1; j < results_aux.length; j++)
				resultsWiki[j] = resultsWiki[j] + results_aux[j];
			resultsWiki[0] = results_aux[0];
		}

		for (int i = 1; i < resultsWiki.length; i++) {
			resultsWiki[i] = resultsWiki[i] / classesWikiEn.length;
		}

		System.out.println();
		System.out.println("Macro-Average results for all classes...");
		System.out.println("Accuracy : " + resultsWiki[0]);
		System.out.println("Precision : " + resultsWiki[1]);
		System.out.println("Recall : " + resultsWiki[2]);
		System.out.println("F1 : " + (2.0 * ((resultsWiki[1] * resultsWiki[2]) / (resultsWiki[1] + resultsWiki[2]))));
	}

	public static void testAIMED() throws Exception {
		System.out.println();
		System.out.println("Test classification on AIMED...");
		double[] results = new double[] { 0.0, 0.0, 0.0, 0.0 };
		for (int i = 1; i <= 10; i++) {
			System.out.println();
			System.out.println("Results for fold " + i + "...");
			System.out.println("Reading train data ...");
			readTrainData("shingles/train-data-aimed.txt." + i);
			System.out.println("Reading test data ...");
			LinkedList<Pair<String, String>> aux = evaluateTestData("shingles/test-data-aimed.txt." + i);
			double[] results_aux = evaluateResults(aux, "related");
			System.out.println("Accuracy  : " + results_aux[0]);
			for (int j = 0; j < results_aux.length; j++) {
				results[j] = results[j] + results_aux[j];
			}
		}

		for (int j = 0; j < results.length; j++)
			results[j] = results[j] / 10;

		System.out.println();
		System.out.println("Avg. results for 10 fold cross validation");
		System.out.println("Accuracy  : " + results[0]);
		System.out.println("Precision : " + results[1]);
		System.out.println("Recall 	  : " + results[2]);
		System.out.println("F1 		  : " + (2.0 * ((results[1] * results[2]) / (results[1] + results[2]))));
	}

	public static void testWikiPT() throws Exception {
		System.out.println();
		System.out.println("Test classification on WikiPT...");
		System.out.println("Reading train data WikiPT...");
		readTrainData("shingles/train-data-wikipt.txt");
		System.out.println("Reading test data WikiPT...");
		LinkedList<Pair<String, String>> all_results = evaluateTestData("shingles/test-data-wikipt.txt");
		double[] results = { 0.0, 0.0, 0.0, 0.0 };

		String[] classes_simmetrycal = { "locatedInArea", "origin", "partOf",
				"deathOrBurialPlace", "successor", "keyPerson", "parent",
				"influencedBy", "partner", "other" };
		
		String[] classes_asymmetrical = { "locatedInArea(e1,e2)",
				"locatedInArea(e2,e1)", "origin(e1,e2)", "origin(e2,e1)",
				"partOf(e1,e2)", "partOf(e2,e1)", "deathOrBurialPlace(e1,e2)",
				"deathOrBurialPlace(e2,e1)", "successor(e1,e2)",
				"successor(e2,e1)", "keyPerson(e1,e2)", "keyPerson(e2,e1)",
				"parent(e1,e2)", "parent(e2,e1)", "influencedBy(e1,e2)",
				"influencedBy(e2,e1)", "partner", "other" };

		//String[] classes = classes_asymmetrical;
		String[] classes = classes_simmetrycal;

		for (String c : classes) {
			double[] results_aux = evaluateResults(all_results, c);
			for (int j = 1; j < results_aux.length; j++)
				results[j] = results[j] + results_aux[j];
			results[0] = results_aux[0];
		}

		for (int i = 1; i < results.length; i++)
			results[i] = results[i] / classes.length;

		System.out.println();
		System.out.println("Total train instances : " + trainInstances);
		System.out.println("Total test instances  : " + testInstances);
		System.out.println("Macro-Average results for all classes...");
		System.out.println("Accuracy  : " + results[0]);
		System.out.println("Precision : " + results[1]);
		System.out.println("Recall    : " + results[2]);
		System.out.println("F1        : " + (2.0 * ((results[1] * results[2]) / (results[1] + results[2]))));
		System.out.println();
	}
}