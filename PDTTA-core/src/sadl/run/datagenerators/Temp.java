package sadl.run.datagenerators;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import sadl.constants.Algoname;
import sadl.constants.AnomalyInsertionType;
import sadl.constants.EventsCreationStrategy;
import sadl.constants.KDEFormelVariant;
import sadl.input.TimedInput;
import sadl.input.TimedWord;
import sadl.modellearner.ButlaPdtaLearner;
import sadl.modellearner.TauPtaLearner;
import sadl.models.TauPTA;
import sadl.models.pta.Event;
import sadl.utils.CollectionUtils;
import sadl.utils.IoUtils;
import sadl.utils.MasterSeed;

public class Temp {

	public static final String TRAIN_TEST_SEP = "?????????????????????????";
	private static final double ANOMALY_PERCENTAGE = 0.1;
	private static final int TRAIN_SIZE = 9000;
	private static final int TEST_SIZE = 4000;
	private static final int SAMPLE_FILES = 11;
	Random r;

	private static Logger logger = LoggerFactory.getLogger(Temp.class);

	public static void main(String[] args) throws IOException {
		final Temp t = new Temp();
		t.temp(args[0]);
	}

	void temp(String dataString) throws IOException {
		final Path outputDir = Paths.get("output");
		IoUtils.cleanDir(outputDir);
		if (r == null) {
			r = MasterSeed.nextRandom();
		}
		final Path confFile = Paths.get("conf-template.txt");
		final String dataType = "real";
		final DecimalFormat df = new DecimalFormat("00");
		final EventsCreationStrategy[] splitEvents = { EventsCreationStrategy.DontSplitEvents, EventsCreationStrategy.SplitEvents };
		TimedInput trainingTimedSequences;
		TimedInput splitTrainingTimedSequences = null;
		logger.info("Parsing input file {}...", dataString);
		final TimedInput unsplitTrainingTimedSequences = TimedInput.parseAlt(Paths.get(dataString), 1);
		logger.info("Parsed input file.");
		for (final AnomalyInsertionType type : AnomalyInsertionType.values()) {
			if (type == AnomalyInsertionType.NONE) {
				continue;
			}

			final Path confDir = outputDir.resolve("confs");
			for (final EventsCreationStrategy split : splitEvents) {
				if (split == EventsCreationStrategy.SplitEvents) {
					if (splitTrainingTimedSequences == null) {
						final ButlaPdtaLearner butla = new ButlaPdtaLearner(10000, EventsCreationStrategy.SplitEvents, KDEFormelVariant.OriginalKDE);
						logger.info("Splitting input into subevents...");
						final Pair<TimedInput, Map<String, Event>> p = butla.splitEventsInTimedSequences(unsplitTrainingTimedSequences);
						splitTrainingTimedSequences = p.getKey();
						logger.info("Split input into subevents.");
					}
					trainingTimedSequences = splitTrainingTimedSequences;
				} else {
					trainingTimedSequences = unsplitTrainingTimedSequences;
				}
				final String genFolder = "tpta-" + (split == EventsCreationStrategy.SplitEvents ? "prep" : "noPrep");
				final int typeIndex = type.getTypeIndex();
				final String typeFolderString = typeIndex == AnomalyInsertionType.ALL.getTypeIndex() ? "mixed" : "type" + typeIndex;
				final Path typeFolder = Paths.get(dataType).resolve(genFolder).resolve(Paths.get(typeFolderString));
				final TimedInput foo = trainingTimedSequences;
				final Consumer<Path> anomalyGenerator = (Path dataOutputFile) -> {
					try {
						generateModelAnomaly(foo, dataOutputFile, type);
					} catch (final Exception e) {
						e.printStackTrace();
						logger.error("Unexpected exception", e);
					}
				};
				createFiles(outputDir, confFile, dataType, df, confDir, genFolder, typeFolderString, typeFolder, anomalyGenerator);
			}

			// randomly
			final String genFolder = "random";
			final int typeIndex = type.getTypeIndex();
			final String typeFolderString = typeIndex == AnomalyInsertionType.ALL.getTypeIndex() ? "mixed" : "type" + typeIndex;
			final Path typeFolder = Paths.get(dataType).resolve(genFolder).resolve(Paths.get(typeFolderString));
			final Consumer<Path> anomalyGenerator = (Path dataOutputFile) -> {
				try {
					generateRandomAnomaly(unsplitTrainingTimedSequences, dataOutputFile, type);
				} catch (final Exception e) {
					e.printStackTrace();
					logger.error("Unexpected exception", e);
				}
			};
			createFiles(outputDir, confFile, dataType, df, confDir, genFolder, typeFolderString, typeFolder, anomalyGenerator);

		}
	}

	private void generateModelAnomaly(TimedInput foo, Path dataOutputFile, AnomalyInsertionType type) throws IOException {
		if (type != AnomalyInsertionType.ALL) {
			generateSingleModelAnomaly(foo, dataOutputFile, type);
		} else {
			generateMixedModelAnomaly(foo, dataOutputFile);
		}
	}

	public void createFiles(final Path outputDir, final Path confFile, final String dataType, final DecimalFormat df, final Path confDir,
			final String genFolder, final String typeFolderString, final Path typeFolder, Consumer<Path> anomalyGenerator) throws IOException {
		for (int k = 0; k < SAMPLE_FILES; k++) {
			final String destFolder = k == 0 ? "train" : "test";
			final Path dataFolder = outputDir.resolve("smac-data").resolve(Paths.get(dataType)).resolve(genFolder).resolve(typeFolderString)
					.resolve(destFolder);
			final Path dataOutputFile = dataFolder.resolve(Paths.get(genFolder + "-" + df.format(k) + "_smac_" + typeFolderString + ".txt"));
			if (Files.notExists(dataFolder)) {
				Files.createDirectories(dataFolder);
			}
			anomalyGenerator.accept(dataOutputFile);
			logger.info("Wrote file #{} ({})", Integer.toString(k), dataOutputFile);
		}
		final String fileSuffix = "-" + dataType + "-" + genFolder + "-" + typeFolderString + ".txt";
		createConfDir(confFile, confDir, typeFolder, fileSuffix);
	}

	public void createConfDir(final Path confFile, final Path confDir, final Path typeFolder, final String fileSuffix) throws IOException {
		for (final Algoname algo : Algoname.values()) {
			if (Files.notExists(confDir.resolve(algo.name().toLowerCase()))) {
				Files.createDirectories(confDir.resolve(algo.name().toLowerCase()));
				logger.info("Created directory {}", confDir.resolve(algo.name().toLowerCase()));
			}
			final String fileName = algo.name().toLowerCase() + fileSuffix;
			final Path destFile = confDir.resolve(algo.name().toLowerCase()).resolve(fileName);
			Files.copy(confFile, destFile);
			final List<String> lines = Files.readAllLines(destFile);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				line = line.replaceAll("\\$algoname", Matcher.quoteReplacement(algo.name().toLowerCase()));
				line = line.replaceAll("\\$trainFolder", Matcher.quoteReplacement(typeFolder.resolve("train").toString()));
				line = line.replaceAll("\\$testFolder", Matcher.quoteReplacement(typeFolder.resolve("test").toString()));
				lines.set(i, line);
			}
			Files.write(destFile, lines, StandardOpenOption.WRITE);
			logger.info("Wrote file {}", destFile);
		}
	}

	Map<Path, Pair<TauPTA, TauPTA>> singleModelPtas = new HashMap<>();
	private void generateSingleModelAnomaly(TimedInput trainingTimedSequences, Path dataOutputFile, AnomalyInsertionType type) throws IOException {
		final List<TimedWord> trainSequences = new ArrayList<>();
		final List<TimedWord> testSequences = new ArrayList<>();
		final Path key = dataOutputFile.getParent().getParent();
		final Pair<TauPTA, TauPTA> result = singleModelPtas.get(key);
		TauPTA pta;
		TauPTA anomaly;
		if (result == null) {
			logger.info("Learning TPTA for single anomaly of type {}...", type);
			final TauPtaLearner learner = new TauPtaLearner();
			pta = learner.train(trainingTimedSequences);
			anomaly = SerializationUtils.clone(pta);
			logger.info("inserting Anomaly Type {} into tpta", type);
			anomaly.makeAbnormal(type);
			if (type == AnomalyInsertionType.TYPE_TWO) {
				anomaly.removeAbnormalSequences(pta);
			}
			for (int i = 0; i < TRAIN_SIZE; i++) {
				trainSequences.add(pta.sampleSequence());
			}
			singleModelPtas.put(key, Pair.of(pta, anomaly));
		} else {
			logger.info("Using cached TPTA.");
			pta = result.getKey();
			anomaly = result.getValue();
		}

		// PTAs of Type 2 and 4 always produce abnormal sequences
		// it is possible to sample abnormal and normal sequences with abnormal ptas of the other types (1,3,5).
		// but I don't know how the distribution is, so to be fair, i sample all anomalies the same
		for (int i = 0; i < TEST_SIZE; i++) {
			if (r.nextDouble() < ANOMALY_PERCENTAGE) {
				boolean wasAnormal = false;
				TimedWord seq = null;
				while (!wasAnormal) {
					seq = anomaly.sampleSequence();
					wasAnormal = seq.isAnomaly();
				}
				testSequences.add(seq);
			} else {
				testSequences.add(pta.sampleSequence());
			}
		}
		final TimedInput trainset = new TimedInput(trainSequences);
		final TimedInput testset = new TimedInput(testSequences);
		try (BufferedWriter bw = Files.newBufferedWriter(dataOutputFile, StandardCharsets.UTF_8)) {
			trainset.toFile(bw, true);
			bw.write('\n');
			bw.write(TRAIN_TEST_SEP);
			bw.write('\n');
			testset.toFile(bw, true);
		}
	}

	int[] intIndex;
	public void generateRandomAnomaly(TimedInput trainingTimedSequences, Path dataOutputFile, AnomalyInsertionType type) throws IOException {
		logger.info("generating random anomalies for type {}...", type);
		final ArrayList<TimedWord> trainSequences = new ArrayList<>();
		final ArrayList<TimedWord> testSequences = new ArrayList<>();
		// final Path p = Paths.get("pta_normal.dot");
		// pta.toGraphvizFile(outputDir.resolve(p), false);
		// final Process ps = Runtime.getRuntime().exec("dot -Tpdf -O " + outputDir.resolve(p));
		// System.out.println(outputDir.resolve(p));
		// ps.waitFor();
		if (intIndex == null || intIndex.length != trainingTimedSequences.size()) {
			intIndex = new int[trainingTimedSequences.size()];
			for (int i = 0; i < trainingTimedSequences.size(); i++) {
				intIndex[i] = i;
			}
		}
		final TIntList shuffledIndex = new TIntArrayList(Arrays.copyOf(intIndex, intIndex.length));
		shuffledIndex.shuffle(r);
		for (int i = 0; i < TRAIN_SIZE; i++) {
			trainSequences.add(trainingTimedSequences.get(shuffledIndex.get(i)));
		}
		for (int i = TRAIN_SIZE; i < TRAIN_SIZE + TEST_SIZE; i++) {
			testSequences.add(trainingTimedSequences.get(shuffledIndex.get(i)));
		}
		if (type != AnomalyInsertionType.NONE) {
			logger.info("inserting random  Anomaly Type {}", type);
			final List<TimedWord> trainSequenceClone = SerializationUtils.clone(trainSequences);
			final List<TimedWord> testSequenceClone = SerializationUtils.clone(testSequences);
			final TimedInput trainSet = new TimedInput(trainSequenceClone);
			TimedInput testSet = new TimedInput(testSequenceClone);
			testSet = testSet.insertRandomAnomalies(type, ANOMALY_PERCENTAGE);

			try (BufferedWriter bw = Files.newBufferedWriter(dataOutputFile, StandardCharsets.UTF_8)) {
				trainSet.toFile(bw, true);
				bw.write('\n');
				bw.write(TRAIN_TEST_SEP);
				bw.write('\n');
				testSet.toFile(bw, true);
				bw.flush();
			}
		}
	}

	private List<TauPTA> createAbnormalPtas(TauPTA normalPta) {
		final List<TauPTA> abnormalPtas = new ArrayList<>();
		for (final AnomalyInsertionType type : AnomalyInsertionType.values()) {
			if (type != AnomalyInsertionType.NONE && type != AnomalyInsertionType.ALL) {
				if (type == AnomalyInsertionType.TYPE_TWO) {
					normalPta = SerializationUtils.clone(normalPta);
					normalPta.setRandom(MasterSeed.nextRandom());
				}
				final TauPTA anomaly = SerializationUtils.clone(normalPta);
				logger.info("inserting Anomaly Type {}", type);
				anomaly.makeAbnormal(type);
				abnormalPtas.add(type.getTypeIndex() - 1, anomaly);
				if (type == AnomalyInsertionType.TYPE_TWO) {
					anomaly.removeAbnormalSequences(normalPta);
				}
			}
		}
		return abnormalPtas;
	}

	Map<Path, Pair<TauPTA, List<TauPTA>>> mixedModelPtas = new HashMap<>();
	private void generateMixedModelAnomaly(TimedInput foo, Path dataOutputFile) throws IOException {
		final Path key = dataOutputFile.getParent().getParent();
		final Pair<TauPTA, List<TauPTA>> result = mixedModelPtas.get(key);
		TauPTA normalPta;
		final List<TauPTA> abnormalPtas;
		if (result == null) {
			logger.info("Learning TPTA for mixed anomalies...");
			final TauPtaLearner learner = new TauPtaLearner();
			normalPta = learner.train(foo);
			abnormalPtas = createAbnormalPtas(normalPta);
			mixedModelPtas.put(key, Pair.of(normalPta, abnormalPtas));
			logger.info("Finished TauPTA creation.");
		} else {
			logger.info("Using cached TPTA.");
			normalPta = result.getKey();
			abnormalPtas = result.getValue();
		}
		final List<TimedWord> trainSequences = new ArrayList<>();
		final List<TimedWord> testSequences = new ArrayList<>();
		// final Path p = Paths.get("pta_normal.dot");
		// pta.toGraphvizFile(outputDir.resolve(p), false);
		// final Process ps = Runtime.getRuntime().exec("dot -Tpdf -O " + outputDir.resolve(p));
		// System.out.println(outputDir.resolve(p));
		// ps.waitFor();
		for (int i = 0; i < TRAIN_SIZE; i++) {
			trainSequences.add(normalPta.sampleSequence());
		}
		for (int i = 0; i < TEST_SIZE; i++) {
			if (r.nextDouble() < ANOMALY_PERCENTAGE) {
				boolean wasAnormal = false;
				TimedWord seq = null;
				final TauPTA chosen = CollectionUtils.chooseRandomObject(abnormalPtas, r);
				while (!wasAnormal) {
					seq = chosen.sampleSequence();
					wasAnormal = seq.isAnomaly();
				}
				testSequences.add(seq);
			} else {
				testSequences.add(normalPta.sampleSequence());
			}
		}
		final TimedInput trainset = new TimedInput(trainSequences);
		final TimedInput testset = new TimedInput(testSequences);
		try (BufferedWriter bw = Files.newBufferedWriter(dataOutputFile, StandardCharsets.UTF_8)) {
			trainset.toFile(bw, true);
			bw.write('\n');
			bw.write(TRAIN_TEST_SEP);
			bw.write('\n');
			testset.toFile(bw, true);
		}
	}
}
