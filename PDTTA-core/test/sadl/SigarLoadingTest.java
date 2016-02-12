/**
 * This file is part of SADL, a library for learning all sorts of (timed) automata and performing sequence-based anomaly detection.
 * Copyright (C) 2013-2016  the original author or authors.
 *
 * SADL is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * SADL is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SADL.  If not, see <http://www.gnu.org/licenses/>.
 */
package sadl;

import sadl.utils.RamGobbler;

public class SigarLoadingTest {

	// @Test
	public void testSigarLoading() throws InterruptedException {
		final RamGobbler gobbler = new RamGobbler();
		gobbler.start();
		Thread.sleep(5000);
		gobbler.shutdown();
		System.out.println("min Ram is " + gobbler.getMinRam());
		System.out.println("max Ram is " + gobbler.getMaxRam());
		System.out.println("average Ram is " + gobbler.getAvgRam());
	}

}
