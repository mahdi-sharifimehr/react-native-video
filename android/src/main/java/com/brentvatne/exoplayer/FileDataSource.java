/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.brentvatne.exoplayer;

import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;

/** A {@link DataSource} for reading local files. */
public final class FileDataSource extends BaseDataSource {

  /**
   * Thrown when IOException is encountered during local file read operation.
   */
  public static class FileDataSourceException extends IOException {

    public FileDataSourceException(IOException cause) {
      super(cause);
    }

  }

  private @Nullable RandomAccessFile file;
  private @Nullable Uri uri;
  private long bytesRemaining;
  private boolean opened;

  public FileDataSource() {
    super(/* isNetwork= */ false);
  }

  /**
   * @param listener An optional listener.
   * @deprecated Use {@link #FileDataSource()} and {@link #addTransferListener(TransferListener)}
   */
  @Deprecated
  public FileDataSource(@Nullable TransferListener listener) {
    this();
    if (listener != null) {
      addTransferListener(listener);
    }
  }

  @Override
  public long open(DataSpec dataSpec) throws FileDataSourceException {
    try {
      RandomAccessFile mfile = new RandomAccessFile(dataSpec.uri.getPath(), "r");
      String file_name = dataSpec.uri.getLastPathSegment().replace(".mp4","");
      int final_skip = 0;
      if(file_name.startsWith("en_")){
        file_name = file_name.replace("en_","");
        int fix_skip = 13;
        byte[] name = file_name.getBytes();
        String hex = String.format("%x", new BigInteger(1, name ));
        int add_skip = Integer.parseInt(hex.substring(0, 2),16);
        int middle_skip = fix_skip + add_skip;
        mfile.seek(middle_skip);
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        byte[] buffer = new byte[2];
        int len = mfile.read(buffer);
        byteBuffer.write(buffer, 0, len);
        byte[] second_byte = byteBuffer.toByteArray();
        int second_skip = (second_byte[1] & 0xff) << 8 | (second_byte[0] & 0xff);
        final_skip = middle_skip + second_skip + 2;
      }

      uri = dataSpec.uri;
      transferInitializing(dataSpec);
      file = new RandomAccessFile(dataSpec.uri.getPath(), "r");
      file.seek(dataSpec.position+final_skip);
      bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? file.length() - dataSpec.position
          : dataSpec.length;
      if (bytesRemaining < 0) {
        throw new EOFException();
      }
    } catch (IOException e) {
      throw new FileDataSourceException(e);
    }

    opened = true;
    transferStarted(dataSpec);

    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws FileDataSourceException {
    if (readLength == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    } else {
      int bytesRead;
      try {
        bytesRead = file.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
      } catch (IOException e) {
        throw new FileDataSourceException(e);
      }

      if (bytesRead > 0) {
        bytesRemaining -= bytesRead;
        bytesTransferred(bytesRead);
      }

      return bytesRead;
    }
  }

  @Override
  public @Nullable Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws FileDataSourceException {
    uri = null;
    try {
      if (file != null) {
        file.close();
      }
    } catch (IOException e) {
      throw new FileDataSourceException(e);
    } finally {
      file = null;
      if (opened) {
        opened = false;
        transferEnded();
      }
    }
  }

}
