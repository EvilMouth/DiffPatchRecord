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

import java.io.IOException;
import java.io.InputStream;

/**
 * A factory that produces multiple independent but identical byte streams exposed via the {@link
 * InputStream} class.
 */
public interface MultiViewInputStreamFactory {
  /**
   * Create and return a new {@link InputStream}. The returned stream is guaranteed to independently
   * produce the same byte sequence as any other stream obtained via a call to this method on the
   * same instance of this object.
   *
   * @return the stream
   * @throws IOException if something goes wrong
   */
  InputStream newStream() throws IOException;
}
