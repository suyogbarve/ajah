/*
 *  Copyright 2011 Eric F. Savage, code@efsavage.com
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package test.ajah.util;

import org.junit.Assert;
import org.junit.Test;

import com.ajah.util.RandomUtils;

/**
 * Tests {@link RandomUtils#getRandomNumber(long, long)}
 * 
 * @author Eric F. Savage <code@efsavage.com>
 */
public class RandomNumberTest {

	/**
	 * Tests a thousand random numbers that should land in-bounds.
	 */
	@Test
	public void testAThousand() {
		final long lower = 1000000;
		final long upper = 10000000;
		for (int attempts = 0; attempts < 1000; attempts++) {
			final long random = RandomUtils.getRandomNumber(lower, upper);
			Assert.assertTrue(random >= lower);
			Assert.assertTrue(random <= upper);
		}
	}

	/**
	 * Test a random distribution within a range for both staying within that
	 * range and being reasonably distributed throughout it.
	 */
	@Test
	public void testRange() {
		final int lower = 10;
		final int upper = 20;
		final int[] array = new int[upper + 1];
		for (int attempts = 0; attempts < 10000; attempts++) {
			array[RandomUtils.getRandomNumber(lower, upper)]++;
		}
		for (int i = 0; i < lower; i++) {
			Assert.assertEquals(0, array[i]);
		}
		for (int i = lower; i <= upper; i++) {
			System.out.println("array[" + i + "] = " + array[i]);
			// Should be extremely improbable that we had less than 5%
			Assert.assertTrue(array[i] > 500);
		}
	}
}
