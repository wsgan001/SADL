/**
 * This file is part of SADL, a library for learning Probabilistic deterministic timed-transition Automata.
 * Copyright (C) 2013-2015  the original author or authors.
 *
 * SADL is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SADL is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SADL.  If not, see <http://www.gnu.org/licenses/>.
 */

package sadl.run;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sadl.constants.AnomalyInsertionType;
import sadl.input.TimedInput;
import sadl.models.TauPTA;
import sadl.utils.IoUtils;

/**
 * 
 * @author Timo Klerx
 *
 */
public class DataGenerator implements Serializable {
	private static Logger logger = LoggerFactory.getLogger(DataGenerator.class);

	private static final long serialVersionUID = -6230657726489919272L;

	String dataString;

	Path outputDir = Paths.get("output");

	/**
	 * @param args
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		final DataGenerator sp = new DataGenerator();
		sp.dataString = args[0];
		logger.info("Running DataGenerator with args" + Arrays.toString(args));
		sp.run();
	}

	private void run() throws IOException, InterruptedException {
		if (Files.notExists(outputDir)) {
			Files.createDirectories(outputDir);
		}
		// parse timed sequences
		final TimedInput trainingTimedSequences = TimedInput.parseAlt(Paths.get(dataString), 1);

		final TauPTA pta = new TauPTA(trainingTimedSequences);
		IoUtils.xmlSerialize(pta, outputDir.resolve(Paths.get("pta_normal.xml")));
		pta.toGraphvizFile(outputDir.resolve(Paths.get("pta_normal.dot")), false);
		// try (BufferedWriter br = Files.newBufferedWriter(outputDir.resolve(Paths.get("normal_sequences")), StandardCharsets.UTF_8)) {
		// logger.info("sampling normal sequences");
		// for (int i = 0; i < 1000000; i++) {
		// br.write(pta.sampleSequence().toString(true));
		// br.write('\n');
		// }
		// }
		// for(final AnomalyInsertionType type : AnomalyInsertionType.values()){

		for (final AnomalyInsertionType type : AnomalyInsertionType.values()) {
			if (type != AnomalyInsertionType.NONE && type != AnomalyInsertionType.ALL) {
				final TauPTA anomaly1 = SerializationUtils.clone(pta);
				logger.info("inserting Anomaly Type {}", type);
				anomaly1.makeAbnormal(type);
				try {
					anomaly1.toGraphvizFile(outputDir.resolve(Paths.get("pta_abnormal_" + type.getTypeIndex() + ".dot")), false);
					IoUtils.xmlSerialize(anomaly1, outputDir.resolve(Paths.get("pta_abnormal_" + type.getTypeIndex() + ".xml")));
					final TauPTA des = (TauPTA) IoUtils.xmlDeserialize(outputDir.resolve(Paths.get("pta_abnormal_" + type.getTypeIndex() + ".xml")));
					if (!anomaly1.equals(des)) {
						throw new IllegalStateException();
					}
				} catch (final IOException e) {
					logger.error("unexpected exception while printing graphviz file", e);
				}
				// try (BufferedWriter br = Files.newBufferedWriter(outputDir.resolve(Paths.get("abnormal_sequences_type_" + type.getTypeIndex())),
				// StandardCharsets.UTF_8)) {
				// for (int i = 0; i < 100000; i++) {
				// br.write(anomaly1.sampleSequence().toString(true));
				// br.write('\n');
				// }
				// }
			}
		}
		logger.info("Starting to dot PTAs");
		final DirectoryStream<Path> ds = Files.newDirectoryStream(outputDir.resolve(Paths.get(".")), "*.dot");
		for (final Path p : ds) {
			if (System.getProperty("os.name").toLowerCase().contains("linux")) {
				logger.info("dotting {}...", p);
				Runtime.getRuntime().exec("dot -Tpdf -O " + p.toString());
				Runtime.getRuntime().exec("dot -Tpng -O " + p.toString());
				logger.info("dotted {}.", p);
			}
		}

	}

}
