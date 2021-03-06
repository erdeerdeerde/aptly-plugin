/*
 * The MIT License
 *
 * Copyright (c) 2017 Zoltan Gyarmati (http://zgyarmati.de)
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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.aptly;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import org.json.JSONObject;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.MultipartBody;

import org.apache.commons.lang.StringUtils;
import java.io.PrintStream;
import java.io.IOException;
import java.io.Console;
import java.util.List;
import java.io.File;

import javax.net.ssl.SSLContext;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.conn.ssl.*;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.security.NoSuchAlgorithmException;



/**
 * This class implements a subset of the Aptly REST client calls
 * as documented at
 * http://www.aptly.info/doc/api/
 * @author $Author: zgyarmati <mr.zoltan.gyarmati@gmail.com>
 */
public class AptlyRestClient {


    private String url;
    private boolean enableSelfSigned;
    private int timeout;
    private String username;
    private String password;
    private PrintStream logger;

    public AptlyRestClient(PrintStream logger, String url, boolean enableSelfSigned,
                            int timeout, String username, String password ){
        this.url = url;
        this.enableSelfSigned  = enableSelfSigned;
        this.timeout  = timeout;
        this.username = username;
        this.password = password;
        this.logger = logger;
        if (enableSelfSigned){
            try{
                SSLContext sslcontext = SSLContexts.custom()
                    .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                    .build();
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext,SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
                CloseableHttpClient httpclient = HttpClients.custom()
                    .setSSLSocketFactory(sslsf)
                    .build();
                Unirest.setHttpClient(httpclient);
            } catch (Exception ex) {
                logger.println("Failed to setup ssl self");
            }
        }
    }

    public String getAptlyServerVersion() throws AptlyRestException {
        String retval = "";
        HttpResponse<JsonNode> jsonResponse;
        try {
            GetRequest req = Unirest.get(this.url +
                                         "/api/version");
            if( username != null && !username.isEmpty()){
                req = req.basicAuth(username, password);
            }
            req = req.header("accept", "application/json");
            jsonResponse = req.asJson();
            logger.println("#################################  Response for version: " + jsonResponse.getBody().toString());
        } catch (UnirestException ex) {
            logger.println("#################################  Failed to get version: " + ex.toString());
            throw new AptlyRestException(ex.toString());
        }
        if (jsonResponse.getStatus() != 200){
            throw new AptlyRestException(jsonResponse.getBody().toString());
        }
        retval = jsonResponse.getBody().getObject().getString("Version");
        return retval;
    }

    public void uploadFiles(List<File> filepaths, String uploaddir) throws AptlyRestException {
        logger.println("upload dir name: " + uploaddir);
        try {
            HttpRequestWithBody req = Unirest.post(this.url +
                                         "/api/files/" + uploaddir);
            req = req.header("accept", "application/json");
            if( username != null && !username.isEmpty()){
                req = req.basicAuth(username, password);
            }
            HttpResponse<JsonNode> jsonResponse = req.field("file", filepaths).asJson();
            logger.printf("Response code: <%d>, body <%s>\n",
                    jsonResponse.getStatus(), jsonResponse.getBody().toString());
            if (jsonResponse.getStatus() != 200){
                throw new AptlyRestException(jsonResponse.getBody().toString());
            }
        } catch (UnirestException ex) {
            logger.printf("Failed to upload the packages: %s\n", ex.toString());
            throw new AptlyRestException(ex.toString());
        }
    }

    public void addUploadedFilesToRepo(String reponame, String uploaddir) throws AptlyRestException {
        // add to the repo
        try {
            HttpRequestWithBody req = Unirest.post(this.url +
                                            "/api/repos/"+ reponame +"/file/" + uploaddir);
            req = req.header("accept", "application/json");
            if( username != null && !username.isEmpty()){
                req = req.basicAuth(username, password);
            }

            HttpResponse<JsonNode> jsonResponse = req.queryString("forceReplace", "1").asJson();

            logger.printf("Response code: <%d>, body <%s>\n",
                    jsonResponse.getStatus(), jsonResponse.getBody().toString());
            if (jsonResponse.getStatus() != 200){
                throw new AptlyRestException(jsonResponse.getBody().toString());
            }
        } catch (UnirestException ex) {
            logger.printf("Failed to add uploaded packages to repo: %s\n", ex.toString());
            throw new AptlyRestException(ex.toString());
        }
    }

    // update published repo
    public void updatePublishRepo(String prefix, String distribution) throws AptlyRestException {
        try {
            HttpRequestWithBody req = Unirest.put(this.url +
                                         "/api/publish/" + prefix + "/" + distribution);
            req = req.header("accept", "application/json");
            req = req.header("Content-Type", "application/json");
            if( username != null && !username.isEmpty()){
                req = req.basicAuth(username, password);
            }
            HttpResponse<JsonNode> jsonResponse = req.body("{\"Signing\":{\"Skip\": true } , \"ForceOverwrite\" : true}").asJson();
            logger.printf("Response code: <%d>, body <%s>\n",
                    jsonResponse.getStatus(), jsonResponse.getBody().toString());
            if (jsonResponse.getStatus() != 200){
                throw new AptlyRestException(jsonResponse.getBody().toString());
            }
        } catch (UnirestException ex) {
            logger.printf("Failed to publish repo: " + ex.toString());
            throw new AptlyRestException(ex.toString());
        }
    }


    /**
     * @return gives back whether a self signed SSL cert is accepted
     */
    boolean getEnableSelfSigned() {
        return this.enableSelfSigned;
    }

    /**
     * @param enableSelfSigned set whether accepting self signed cert
     */
    public void setEnableSelfSigned(boolean enableSelfSigned) {
        this.enableSelfSigned = enableSelfSigned;
    }

    /**
     * @return the timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * @param aTimeout the timeout to set
     */
    public void setTimeout(int aTimeout) {
        timeout = aTimeout;
        Unirest.setTimeouts(timeout * 1000, timeout * 1000);
    }

    /**
     * @return the hostname
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String url) {
        this.url = url;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }
}
