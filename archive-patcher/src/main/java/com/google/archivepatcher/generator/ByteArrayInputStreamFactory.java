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

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * A {@link MultiViewInputStreamFactory} which creates {@link ByteArrayInputStream}s based on the
 * given {@code byte[]} in {@link #ByteArrayInputStreamFactory(byte[])}.
 */
public class ByteArrayInputStreamFactory implements MultiViewInputStreamFactory {

  private final byte[] bytes;

  public ByteArrayInputStreamFactory(byte[] bytes) {
    this.bytes = bytes;
  }

  @Override
  public ByteArrayInputStream newStream() throws IOException {
    return new ByteArrayInputStream(bytes);
  }
}
