/*
 * Copyright 1999-2101 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.akita.widget.image;

import com.alibaba.akita.cache.FilesCache;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class RemoteImageLoaderJob implements Runnable {

    private static final String LOG_TAG = "Ignition/ImageLoader";

    private static final int DEFAULT_RETRY_HANDLER_SLEEP_TIME = 1000;

    private String imageUrl;
    private RemoteImageLoaderHandler handler;
    private FilesCache<Bitmap> imageCache;
    private int numRetries, defaultBufferSize;

    public RemoteImageLoaderJob(String imageUrl, RemoteImageLoaderHandler handler, FilesCache<Bitmap> imageCache,
            int numRetries, int defaultBufferSize) {
        this.imageUrl = imageUrl;
        this.handler = handler;
        this.imageCache = imageCache;
        this.numRetries = numRetries;
        this.defaultBufferSize = defaultBufferSize;
    }

    /**
     * The job method run on a worker thread. It will first query the image cache, and on a miss,
     * download the image from the Web.
     */
    @Override
    public void run() {
        Bitmap bitmap = null;

        if (imageCache != null) {
            // at this point we know the image is not in memory, but it could be cached to SD card
            bitmap = imageCache.get(imageUrl);
        }

        if (bitmap == null) {
            bitmap = downloadImage();
        }

        notifyImageLoaded(imageUrl, bitmap);
    }

    // TODO: we could probably improve performance by re-using connections instead of closing them
    // after each and every download
    protected Bitmap downloadImage() {
        int timesTried = 1;

        while (timesTried <= numRetries) {
            try {
                byte[] imageData = retrieveImageData();

                if (imageData == null) {
                    break;
                }

                Bitmap bm = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                if (imageCache != null && bm != null) {
                    imageCache.put(imageUrl, bm);
                }

                return BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

            } catch (Throwable e) {
                Log.w(LOG_TAG, "download for " + imageUrl + " failed (attempt " + timesTried + ")");
                e.printStackTrace();
                SystemClock.sleep(DEFAULT_RETRY_HANDLER_SLEEP_TIME);
                timesTried++;
            }
        }

        return null;
    }

    protected byte[] retrieveImageData() throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // determine the image size and allocate a buffer
        int fileSize = connection.getContentLength();
        Log.d(LOG_TAG, "fetching image " + imageUrl + " (" + (fileSize <= 0 ? "size unknown" : Integer.toString(fileSize)) + ")");

        BufferedInputStream istream = new BufferedInputStream(connection.getInputStream());

        try {   
            if (fileSize <= 0) {
                Log.w(LOG_TAG,
                        "Server did not set a Content-Length header, will default to buffer size of "
                                + defaultBufferSize + " bytes");
                ByteArrayOutputStream buf = new ByteArrayOutputStream(defaultBufferSize);
                byte[] buffer = new byte[defaultBufferSize];
                int bytesRead = 0;
                while (bytesRead != -1) {
                    bytesRead = istream.read(buffer, 0, defaultBufferSize);
                    if (bytesRead > 0)
                        buf.write(buffer, 0, bytesRead);
                }
                return buf.toByteArray();
            } else {
                byte[] imageData = new byte[fileSize];
        
                int bytesRead = 0;
                int offset = 0;
                while (bytesRead != -1 && offset < fileSize) {
                    bytesRead = istream.read(imageData, offset, fileSize - offset);
                    offset += bytesRead;
                }
                return imageData;
            }
        } finally {
            // clean up
            try {
                istream.close();
                connection.disconnect();
            } catch (Exception ignore) { }
        }
    }

    protected void notifyImageLoaded(String url, Bitmap bitmap) {
        Message message = new Message();
        message.what = RemoteImageLoaderHandler.HANDLER_MESSAGE_ID;
        Bundle data = new Bundle();
        data.putString(RemoteImageLoaderHandler.IMAGE_URL_EXTRA, url);
        Bitmap image = bitmap;
        data.putParcelable(RemoteImageLoaderHandler.BITMAP_EXTRA, image);
        message.setData(data);

        handler.sendMessage(message);
    }
}