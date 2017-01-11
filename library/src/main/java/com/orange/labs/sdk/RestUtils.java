/*
 * Copyright (c) 2014 Orange.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Created by Erwan Morvillez on 07/10/14.
 */
package com.orange.labs.sdk;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.orange.labs.sdk.exception.CloudAPIException;
import com.orange.labs.sdk.exception.OrangeCloudOperationException;
import com.orange.labs.sdk.exception.OrangeAPIException;
import com.orange.labs.sdk.exception.SynchronusException;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.HttpsURLConnection;

public class RestUtils {

    private static int TIMEOUT = 5000;
    private static String TAG = RestUtils.class.toString();

    private RequestQueue mRequestQueue;
    private ImageLoader.ImageCache mImageCache;
    private int maxWidth;
    private int maxHeight;
    private Context mContext;

    public RestUtils(Context context) {
        // Create Volley Request Queue thanks to context
        mRequestQueue = Volley.newRequestQueue(context);
        mContext = context;

        // Fix maxWidth & maxHeight of screen
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);

        maxWidth = metrics.widthPixels;
        maxHeight = metrics.heightPixels;
    }

    public void setCache(ImageLoader.ImageCache cache) {
        mImageCache = cache;
    }

    public void jsonRequest(final String tag,
                            final int method,
                            final String url,
                            final JSONObject params,
                            final Map<String, String> headers,
                            final Response.Listener<JSONObject> success,
                            final OrangeListener.Error failure) {
        Log.v(TAG, "jsonRequest: " + url);
        JsonObjectRequest jsonObjReq = new JsonObjectRequest(method, url, params, success,
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        failure.onErrorResponse(new CloudAPIException(error));
                    }
                }) {

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers;
            }
        };
        jsonObjReq.setRetryPolicy(new DefaultRetryPolicy(
                TIMEOUT,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        jsonObjReq.setTag(tag);
        mRequestQueue.add(jsonObjReq);
    }


    public void stringRequest(final String tag,
                              final int method,
                              final String url,
                              final Map<String, String> params,
                              final Map<String, String> headers,
                              final Response.Listener<String> success,
                              final OrangeListener.Error failure) {
        Log.v(TAG, "stringRequest: " + url);
        StringRequest stringReq = new StringRequest(method, url, success,
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        failure.onErrorResponse(new CloudAPIException(error));
                    }
                }) {
            @Override
            protected Map<String, String> getParams() {
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers;
            }
        };
        stringReq.setRetryPolicy(new DefaultRetryPolicy(
                TIMEOUT,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        stringReq.setTag(tag);
        mRequestQueue.add(stringReq);
    }

    public void imageRequest(final String tag,
                             final String url,
                             final Map<String, String> headers,
                             final OrangeListener.Success<Bitmap> success,
                             final OrangeListener.Error failure,
                             final boolean useCache) {


        if (useCache && mImageCache != null) {
            Bitmap image = mImageCache.getBitmap(tag);
            if (image != null) {
                success.onResponse(image);
                return;
            }
        }
        Log.v(TAG, "imageRequest: " + url);
        ImageRequest request = new ImageRequest(url,
                new Response.Listener<Bitmap>() {
                    @Override
                    public void onResponse(Bitmap bitmap) {
                        if (useCache && mImageCache != null) {
                            mImageCache.putBitmap(tag, bitmap);
                        }
                        success.onResponse(bitmap);
                    }
                }, maxWidth, maxHeight, null,
                new Response.ErrorListener() {
                    public void onErrorResponse(VolleyError error) {
                        failure.onErrorResponse(new CloudAPIException(error));
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return headers;
            }
        };
        // Adding request to request queue
        request.setRetryPolicy(new DefaultRetryPolicy(
                TIMEOUT,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        request.setTag(tag);
        mRequestQueue.add(request);
    }

    public void uploadRequest(final URL url,
                              final Uri fileUri,
                              final Map<String, String> headers,
                              final Response.Listener<JSONObject> success,
                              final OrangeListener.Progress progress,
                              final OrangeListener.Error failure) {


        try {
            Log.v(TAG, "uploadRequest: " + url);
            FileInputStream fileInputStream = (FileInputStream) mContext.getContentResolver().openInputStream(fileUri);

            // Open a HTTP connection to the URL
            HttpURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");

            //
            // Define headers
            //
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            for (String key : headers.keySet()) {
                conn.setRequestProperty(key.toString(), headers.get(key));
            }

            //
            // Write body part
            //
            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

            int bytesAvailable = fileInputStream.available();
            int progressValue = 0;
            int bytesRead = 0;
            byte buf[] = new byte[1024];
            BufferedInputStream bufInput = new BufferedInputStream(fileInputStream);
            while ((bytesRead = bufInput.read(buf)) != -1) {
                // write output
                dos.write(buf, 0, bytesRead);
                dos.flush();
                progressValue += bytesRead;
                // update progress bar
                progress.onProgress((float) progressValue / bytesAvailable);
            }

            //
            // Responses from the server (code and message)
            //
            int serverResponseCode = conn.getResponseCode();
            // close streams
            fileInputStream.close();
            dos.flush();
            dos.close();

            if (serverResponseCode == 200 || serverResponseCode == 201) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(
                        conn.getInputStream()));

                String response = "";
                String line;
                while ((line = rd.readLine()) != null) {
                    Log.i("FileUpload", "Response: " + line);
                    response += line;
                }
                rd.close();
                JSONObject object = new JSONObject(response);
                success.onResponse(object);
            } else {
                BufferedReader rd = new BufferedReader(new InputStreamReader(
                        conn.getErrorStream()));
                String response = "";
                String line;
                while ((line = rd.readLine()) != null) {
                    Log.i("FileUpload", "Error: " + line);
                    response += line;
                }
                rd.close();
                JSONObject errorResponse = new JSONObject(response);
                failure.onErrorResponse(new CloudAPIException(serverResponseCode, errorResponse));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JSONObject jsonRequestSynchronus(final String tag, final String url, final Map<String, String> headers) throws SynchronusException {
        RequestFuture<JSONObject> future = RequestFuture.newFuture();

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(url, null, future, future){
            @Override
            public Map<String, String> getHeaders() {
                return headers;
            }
        };

        jsonObjReq.setTag(tag);
        mRequestQueue.add(jsonObjReq);

        try {
            return future.get(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new SynchronusException(e);
        } catch (ExecutionException e) {
            e.printStackTrace();
            throw new SynchronusException(e);
        } catch (TimeoutException e) {
            e.printStackTrace();
            throw new SynchronusException(e);
        }
    }

    public void uploadRequestSynchronus(URL url, final Uri fileUri, final Map<String, String> headers) throws OrangeCloudOperationException, OrangeAPIException {
        try {
            FileInputStream fileInputStream = (FileInputStream) mContext.getContentResolver().openInputStream(fileUri);
            int mSizeFile = (int) fileInputStream.getChannel().size();

            // Open a HTTP connection to the URL
            HttpURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");

            //
            // Define headers
            //
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            for (String key : headers.keySet()) {
                conn.setRequestProperty(key.toString(), headers.get(key));
            }

            //
            // Write body part
            //
            DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());
            IOUtils.copy(fileInputStream, outputStream);

            //
            // Responses from the server (code and message)
            //
            int serverResponseCode = conn.getResponseCode();
            // close streams
            fileInputStream.close();
            outputStream.close();

            if (serverResponseCode == 200 || serverResponseCode == 201) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                String response = "";
                String line;
                while ((line = rd.readLine()) != null) {
                    Log.i("FileUpload", "Response: " + line);
                    response += line;
                }
                rd.close();
            } else {
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                String response = "";
                String line;
                while ((line = rd.readLine()) != null) {
                    Log.i("FileUpload", "Error: " + line);
                    response += line;
                }
                rd.close();
                throw new OrangeAPIException(serverResponseCode, "", "FileUpload Error", response);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        }
    }

    public void downloadRequestSynchronus(URL url, String folder, final Map<String, String> headers) throws OrangeCloudOperationException {
        try {
            JSONObject result = jsonRequestSynchronus(url.toString(), url.toString(), headers);
            File file = new File(folder+"/"+result.getString("name"));
            long fileSize = result.getInt("size");
            if(file.exists()){
                if (file.length()!=fileSize){
                    file.delete();
                } else return ;
            }

            String downloadAddress = result.getString("downloadUrl");
            URL downloadUrl = new URL(downloadAddress);
            URLConnection downloadConnection = downloadUrl.openConnection();
            for (String key : headers.keySet()) {
                downloadConnection.setRequestProperty(key.toString(), headers.get(key));
            }

            InputStream inputStream = downloadConnection.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(folder+"/"+result.getString("name"));

            IOUtils.copy(inputStream, outputStream);

            outputStream.close();
            inputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (SynchronusException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        }
    }


    public void uploadRequestStreamSynchronus(URL url, InputStream inputStream, final Map<String, String> headers) throws OrangeCloudOperationException, OrangeAPIException {
        try {
            // Open a HTTP connection to the URL
            HttpURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");

            //
            // Define headers
            //
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            for (String key : headers.keySet()) {
                conn.setRequestProperty(key.toString(), headers.get(key));
            }

            //
            // Write body part
            //
            DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());
            IOUtils.copy(inputStream, outputStream);

            //
            // Responses from the server (code and message)
            //
            int serverResponseCode = conn.getResponseCode();
            // close streams
            inputStream.close();
            outputStream.flush();
            outputStream.close();

            if (serverResponseCode == 200 || serverResponseCode == 201) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                String response = "";
                String line;
                while ((line = rd.readLine()) != null) {
                    Log.i("FileUpload", "Response: " + line);
                    response += line;
                }
                rd.close();
            } else {
                BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                String response = "";
                String line;
                while ((line = rd.readLine()) != null) {
                    Log.i("FileUpload", "Error: " + line);
                    response += line;
                }
                rd.close();
                throw new OrangeAPIException(serverResponseCode, "", "FileUpload Error", response);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        }
    }

    public void downloadRequestStreamSynchronus(URL url, OutputStream outputStream, final Map<String, String> headers) throws OrangeCloudOperationException {
        try {
            JSONObject result = jsonRequestSynchronus(url.toString(), url.toString(), headers);

            String downloadAddress = result.getString("downloadUrl");
            URL downloadUrl = new URL(downloadAddress);
            URLConnection downloadConnection = downloadUrl.openConnection();
            for (String key : headers.keySet()) {
                downloadConnection.setRequestProperty(key.toString(), headers.get(key));
            }
            BufferedInputStream inputStream = new BufferedInputStream(downloadConnection.getInputStream());

            IOUtils.copy(inputStream, outputStream);

            outputStream.close();
            inputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (ProtocolException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            throw new OrangeCloudOperationException(npe);
        } catch (SynchronusException e) {
            e.printStackTrace();
            throw new OrangeCloudOperationException(e);
        }
    }
}
