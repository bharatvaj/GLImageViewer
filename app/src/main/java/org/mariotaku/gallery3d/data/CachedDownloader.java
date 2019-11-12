/*
 * Copyright (C) 2010 The Android Open Source Project
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

package org.mariotaku.gallery3d.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URL;

import org.mariotaku.gallery3d.common.Utils;
import org.mariotaku.gallery3d.util.ThreadPool.CancelListener;
import org.mariotaku.gallery3d.util.ThreadPool.JobContext;
import org.mariotaku.twidere.util.EnvironmentAccessor;

import android.content.Context;

public class CachedDownloader {

	public static final String CACHE_DIR_NAME = "cached_images";

	private final Context mContext;
	private File mCacheRoot;

	public CachedDownloader(final Context context) {
		mContext = context;
		initCacheDir();
	}

	public File download(final JobContext jc, final String url) throws IOException {
		final File file = getCacheFile(url);
		if (file == null) return null;
		if (!file.exists() || file.length() == 0) {
			final InputStream input = new URL(url).openStream();
			final FileOutputStream output = new FileOutputStream(file);
			try {
				dump(jc, input, output);
			} finally {
				Utils.closeSilently(input);
			}
		}
		return file;
	}

	private File getBestCacheDir(final String cache_dir_name) {
		final File ext_cache_dir = EnvironmentAccessor.getExternalCacheDir(mContext);
		if (ext_cache_dir != null && ext_cache_dir.isDirectory()) {
			final File cache_dir = new File(ext_cache_dir, cache_dir_name);
			if (cache_dir.isDirectory() || cache_dir.mkdirs()) return cache_dir;
		}
		return new File(mContext.getCacheDir(), cache_dir_name);
	}

	private File getCacheFile(final String url) {
		if (url == null) return null;
		return new File(mCacheRoot, url.replaceAll("https?:\\/\\/", "").replaceAll("[^\\w\\d]", "_"));
	}

	void initCacheDir() {
		mCacheRoot = getBestCacheDir(CACHE_DIR_NAME);
	}

	private static void dump(final JobContext jc, final InputStream is, final OutputStream os) throws IOException {
		final byte buffer[] = new byte[8192];
		int rc = is.read(buffer, 0, buffer.length);
		final Thread thread = Thread.currentThread();
		jc.setCancelListener(new CancelListener() {
			@Override
			public void onCancel() {
				thread.interrupt();
			}
		});
		while (rc > 0) {
			if (jc.isCancelled()) throw new InterruptedIOException();
			os.write(buffer, 0, rc);
			rc = is.read(buffer, 0, buffer.length);
		}
		jc.setCancelListener(null);
		Thread.interrupted(); // consume the interrupt signal
	}
}