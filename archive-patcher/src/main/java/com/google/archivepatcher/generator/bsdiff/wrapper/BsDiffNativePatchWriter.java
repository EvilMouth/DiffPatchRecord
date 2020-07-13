// Copyright 2017 Google LLC. All rights reserved.
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

package com.google.archivepatcher.generator.bsdiff.wrapper;

import com.google.archivepatcher.shared.bytesource.ByteSource;
import com.google.archivepatcher.shared.bytesource.FileByteSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Generates BsDiff patches using the native implementation, by using JNI. */
public class BsDiffNativePatchWriter {
  /**
   * Generate a diff between the old file and the new, writing to the specified stream. Uses the
   * default match length of 8.
   *
   * @param oldFile the old file
   * @param newFile the new file
   * @param deltaOut where output should be written
   * @throws IOException if unable to read or write data
   */
  public static void generatePatch(File oldFile, File newFile, OutputStream deltaOut)
      throws IOException {
    byte[] patch = nativeGeneratePatchFile(oldFile.getPath(), newFile.getPath());

    if (patch == null) {
      throw new IllegalStateException("Unable to generate patch.");
    }

    deltaOut.write(patch);
  }

  /**
   * Generate a diff between the old data and the new, writing to the specified stream. Uses the
   * default match length of 8.
   *
   * @param oldBlob the old data
   * @param newBlob the new data
   * @param deltaOut where output should be written
   * @throws IOException if unable to read or write data
   */
  public static void generatePatch(ByteSource oldBlob, ByteSource newBlob, OutputStream deltaOut)
      throws IOException {
    if (oldBlob instanceof FileByteSource && newBlob instanceof FileByteSource) {
      generatePatch(
          ((FileByteSource) oldBlob).getFile(), ((FileByteSource) newBlob).getFile(), deltaOut);
    } else {
      byte[] oldData = new byte[(int) oldBlob.length()];
      byte[] newData = new byte[(int) newBlob.length()];
      try (InputStream in = oldBlob.openStream()) {
        in.read(oldData);
      }
      try (InputStream in = newBlob.openStream()) {
        in.read(newData);
      }
      byte[] patch = nativeGeneratePatchData(oldData, newData);

      if (patch == null) {
        throw new IllegalStateException("Unable to generate patch.");
      }

      deltaOut.write(patch);
    }
  }

  /**
   * Generate a diff between the old data and the new, writing to the specified stream. Relies on
   * the native implementation of BsDiff for match length.
   *
   * @param oldData the old data
   * @param newData the new data
   * @param deltaOut where output should be written
   * @throws IOException if unable to read or write data
   */
  public static void generatePatch(byte[] oldData, byte[] newData, OutputStream deltaOut)
      throws IOException {
    byte[] patch = nativeGeneratePatchData(oldData, newData);

    if (patch == null) {
      throw new IllegalStateException("Unable to generate patch.");
    }

    deltaOut.write(patch);
  }

  /**
   * @param oldFile path to the old file used to generate the patch
   * @param newFile path to the new file used to generate the patch
   * @return a byte array containing the generated patch
   */
  private static native byte[] nativeGeneratePatchFile(String oldFile, String newFile);

  /**
   * @param oldData data of old file used to generate the patch
   * @param newData data of new file used to generate the patch
   * @return a byte array containing the generated patch
   */
  private static native byte[] nativeGeneratePatchData(byte[] oldData, byte[] newData);
}
