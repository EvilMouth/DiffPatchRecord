// Copyright 2016 Google LLC. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.archivepatcher.generator;

import static com.google.archivepatcher.shared.bytesource.ByteStreams.COPY_BUFFER_SIZE;
import static com.google.archivepatcher.shared.bytesource.ByteStreams.copy;
import static com.google.archivepatcher.shared.bytesource.ByteStreams.readFully;

import com.google.archivepatcher.shared.DefaultDeflateCompatibilityWindow;
import com.google.archivepatcher.shared.InflaterWrapper;
import com.google.archivepatcher.shared.JreDeflateParameters;
import com.google.archivepatcher.shared.PatchConstants.CompressionMethod;
import com.google.archivepatcher.shared.bytesource.ByteSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

/**
 * Divines information about the compression used for a resource that has been compressed with a
 * deflate-compatible algorithm. This implementation produces results that are valid within the
 * {@link DefaultDeflateCompatibilityWindow}.
 */
public class DefaultDeflateCompressionDiviner {

  /** The levels to try for each strategy, in the order to attempt them. */
  private static final Map<Integer, List<Integer>> LEVELS_BY_STRATEGY = getLevelsByStrategy();

  /**
   * A simple struct that contains a {@link MinimalZipEntry} describing a specific entry from a zip
   * archive along with an optional accompanying {@link JreDeflateParameters} describing the
   * original compression settings that were used to generate the compressed delivery in that entry.
   */
  public static class DivinationResult {
    /**
     * The {@link MinimalZipEntry} for the result; never null.
     */
    public final MinimalZipEntry minimalZipEntry;

    /**
     * The {@link JreDeflateParameters} for the result, possibly null. This value is only set if
     * {@link MinimalZipEntry} is deflate compressed <em>and</em> the compression settings were
     * successfully divined.
     */
    public final JreDeflateParameters divinedParameters;

    /**
     * Creates a new result with the specified fields.
     * @param minimalZipEntry the zip entry
     * @param divinedParameters the parameters
     */
    public DivinationResult(
        MinimalZipEntry minimalZipEntry, JreDeflateParameters divinedParameters) {
      if (minimalZipEntry == null) {
        throw new IllegalArgumentException("minimalZipEntry cannot be null");
      }
      this.minimalZipEntry = minimalZipEntry;
      this.divinedParameters = divinedParameters;
    }
  }

  /**
   * Load the specified archive and attempt to divine deflate parameters for all entries within.
   *
   * @param archiveBlob the archive blob to work on
   * @return a list of results for each entry in the archive, in file order (not central directory
   *     order). There is exactly one result per entry, regardless of whether or not that entry is
   *     compressed. Callers can filter results by checking {@link
   *     MinimalZipEntry#compressionMethod()} to see if the result is or is not compressed, and by
   *     checking whether a non-null {@link JreDeflateParameters} was obtained.
   * @throws IOException if unable to read or parse the file
   * @see DivinationResult
   */
  public static List<DivinationResult> divineDeflateParameters(File archiveBlob)
      throws IOException {
    try (ByteSource archiveData = ByteSource.fromFile(archiveBlob)) {
      return divineDeflateParameters(archiveData);
    }
  }

  /**
   * Load the specified archive and attempt to divine deflate parameters for all entries within.
   *
   * @param archiveBlob the archive blob to work on
   * @return a list of results for each entry in the archive, in file order (not central directory
   *     order). There is exactly one result per entry, regardless of whether or not that entry is
   *     compressed. Callers can filter results by checking {@link
   *     MinimalZipEntry#compressionMethod()} to see if the result is or is not compressed, and by
   *     checking whether a non-null {@link JreDeflateParameters} was obtained.
   * @throws IOException if unable to read or parse the file
   * @see DivinationResult
   */
  public static List<DivinationResult> divineDeflateParameters(ByteSource archiveBlob)
      throws IOException {
    List<MinimalZipEntry> zipEntries = MinimalZipArchive.listEntries(archiveBlob);
    List<DivinationResult> results = new ArrayList<>(zipEntries.size());
    for (MinimalZipEntry minimalZipEntry : zipEntries) {
      JreDeflateParameters divinedParameters = null;
      if (minimalZipEntry.compressionMethod() == CompressionMethod.DEFLATE) {
        // Keep small entries in memory to avoid unnecessary file I/O.
        if (minimalZipEntry.compressedDataRange().length() < (100 * 1024)) {
          try {
            byte[] compressedBytes = new byte[(int) minimalZipEntry.compressedDataRange().length()];
            try (InputStream in =
                archiveBlob.slice(minimalZipEntry.compressedDataRange()).openStream()) {
              readFully(in, compressedBytes);
            }
            divinedParameters = divineDeflateParametersForEntry(ByteSource.wrap(compressedBytes));
          } catch (Exception ignore) {
            divinedParameters = null;
          }
        } else {
          try (ByteSource slice = archiveBlob.slice(minimalZipEntry.compressedDataRange())) {
            divinedParameters = divineDeflateParametersForEntry(slice);
          }
        }
      }
      results.add(new DivinationResult(minimalZipEntry, divinedParameters));
    }
    return results;
  }

  /**
   * Determines the original {@link JreDeflateParameters} that were used to compress a given piece
   * of deflated delivery.
   *
   * @param entry a {@link MultiViewInputStreamFactory} that can provide multiple independent {@link
   *     InputStream} instances for the compressed delivery.
   * @return the parameters that can be used to replicate the compressed delivery in the {@link
   *     DefaultDeflateCompatibilityWindow}, if any; otherwise <code>null</code>. Note that <code>
   *     null</code> is also returned in the case of <em>corrupt</em> zip delivery since, by
   *     definition, it cannot be replicated via any combination of normal deflate parameters.
   * @throws IOException if there is a problem reading the delivery, i.e. if the file contents are
   *     changed while reading
   */
  public static JreDeflateParameters divineDeflateParametersForEntry(ByteSource entry)
      throws IOException {
    // Iterate over all relevant combinations of nowrap, strategy and level.
    for (boolean nowrap : new boolean[] {true, false}) {
      InflaterWrapper inflater = new InflaterWrapper(nowrap);
      Deflater deflater = new Deflater(0, nowrap);

      strategy_loop:
      for (int strategy : new int[] {0, 1, 2}) {
        deflater.setStrategy(strategy);
        for (int level : LEVELS_BY_STRATEGY.get(strategy)) {
          deflater.setLevel(level);
          inflater.reset();
          deflater.reset();
          try {
            if (matches(inflater, deflater, entry)) {
              end(inflater, deflater);
              return JreDeflateParameters.of(level, strategy, nowrap);
            }
          } catch (ZipException e) {
            // Parse error in input. The only possibilities are corruption or the wrong nowrap.
            // Skip all remaining levels and strategies.
            break strategy_loop;
          }
        }
      }
      end(inflater, deflater);
    }
    return null;
  }

  /**
   * Returns an unmodifiable map whose keys are deflate strategies and whose values are the levels
   * that make sense to try with the corresponding strategy, in the recommended testing order.
   *
   * <ul>
   *   <li>For strategy 0, levels 1 through 9 (inclusive) are included.
   *   <li>For strategy 1, levels 4 through 9 (inclusive) are included. Levels 1, 2 and 3 are
   *       excluded because they behave the same under strategy 0.
   *   <li>For strategy 2, only level 1 is included because the level is ignored under strategy 2.
   * </ul>
   *
   * @return such a mapping
   */
  private static Map<Integer, List<Integer>> getLevelsByStrategy() {
    final Map<Integer, List<Integer>> levelsByStrategy = new HashMap<>();
    // The best order for the levels is simply the order of popularity in the world, which is
    // expected to be default (6), maximum compression (9), and fastest (1).
    // The rest of the levels are rarely encountered and their order is mostly irrelevant.
    levelsByStrategy.put(0, Collections.unmodifiableList(Arrays.asList(6, 9, 1, 4, 2, 3, 5, 7, 8)));
    levelsByStrategy.put(1, Collections.unmodifiableList(Arrays.asList(6, 9, 4, 5, 7, 8)));
    // Strategy 2 does not have the concept of levels, so vacuously call it 1.
    levelsByStrategy.put(2, Collections.singletonList(1));
    return Collections.unmodifiableMap(levelsByStrategy);
  }

  /**
   * Closes the (de)compressor and discards any unprocessed input. This method should be called when
   * the (de)compressor is no longer being used. Once this method is called, the behavior
   * De/Inflater is undefined.
   *
   * @see Inflater#end
   * @see Deflater#end
   */
  private static void end(InflaterWrapper inflater, Deflater deflater) {
    inflater.endInternal();
    deflater.end();
  }

  /**
   * Checks whether the specified deflater will produce the same compressed delivery as the byte
   * stream.
   *
   * @param inflater the inflater for uncompressing the stream
   * @param deflater the deflater for recompressing the output of the inflater
   * @param compressedData {@link ByteSource} containing the compressed data.
   * @param copyBuffer buffer to use for copying bytes between the inflater and the deflater
   * @return true if the specified deflater reproduces the bytes in compressedDataIn, otherwise
   *     false
   * @throws IOException if anything goes wrong; in particular, {@link ZipException} is thrown if
   *     there is a problem parsing compressedDataIn
   */
  private static boolean matches(Inflater inflater, Deflater deflater, ByteSource compressedData)
      throws IOException {

    try (MatchingOutputStream matcher =
            new MatchingOutputStream(compressedData.openStream(), COPY_BUFFER_SIZE);
        InflaterInputStream inflaterIn =
            new InflaterInputStream(compressedData.openStream(), inflater, COPY_BUFFER_SIZE);
        DeflaterOutputStream out = new DeflaterOutputStream(matcher, deflater, COPY_BUFFER_SIZE)) {
      copy(inflaterIn, out);
      // When done, all bytes have been successfully recompressed. For sanity, check that
      // the matcher has consumed the same number of bytes and arrived at EOF as well.
      out.finish();
      out.flush();
      matcher.expectEof();
      // At this point the delivery in the compressed output stream was a perfect match for the
      // delivery in the compressed input stream; the answer has been found.
      return true;
    } catch (MismatchException e) {
      // Fast-fail case when the compressed output stream doesn't match the compressed input
      // stream. These are not the parameters you're looking for!
      return false;
    }
  }

  private DefaultDeflateCompressionDiviner() {}
}
