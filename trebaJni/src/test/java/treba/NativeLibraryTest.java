package treba;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import utils.LibraryChecker;

public class NativeLibraryTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws IOException {
		final String osName = System.getProperty("os.name");
		Path p = null;
		if (osName.toLowerCase().contains("linux") && LibraryChecker.trebaDepsInstalled()) {
			assertTrue(trebaJNI.isLibraryLoaded());
		} else if (osName.toLowerCase().contains("windows")) {
			try {
				p = trebaJNI.findLibrary();
				assertNotNull(p);
				try {
					trebaJNI.loadLibrary(p);
					fail("not possible to load treba in windows");
				} catch (final UnsatisfiedLinkError e) {
					// expected
				}
			} catch (final IOException e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}

}
