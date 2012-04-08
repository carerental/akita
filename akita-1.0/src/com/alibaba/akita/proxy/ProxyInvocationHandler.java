/*
 * Copyright 2011 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package com.alibaba.akita.proxy;

import com.alibaba.akita.exception.AkInvokeException;
import com.alibaba.akita.io.HttpInvoker;
import com.alibaba.akita.proxy.annotation.*;
import com.alibaba.akita.util.JsonMapper;
import com.alibaba.akita.util.Log;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.codehaus.jackson.JsonProcessingException;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Dynamic Proxy Invocation Handler
 * @author zhe.yangz 2011-12-28 下午04:47:56
 */
public class ProxyInvocationHandler implements InvocationHandler {
    
    private static final String TAG = "ProxyInvocationHandler";

    public Object bind(Class<?> clazz) {
        Class<?>[] clazzs = {clazz};
        Object newProxyInstance = Proxy.newProxyInstance(
                clazz.getClassLoader(), 
                clazzs, 
                this);
        return newProxyInstance;
    }
    
    /** 
     * Dynamic proxy invoke
     */ 
    public Object invoke(Object proxy, Method method, Object[] args)  
            throws Throwable {
        AkPOST akPost = method.getAnnotation(AkPOST.class);
        AkGET akGet = method.getAnnotation(AkGET.class);
        
        AkAPI akApi = method.getAnnotation(AkAPI.class);
        Annotation[][] annosArr = method.getParameterAnnotations();
        String invokeUrl = akApi.url();
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        
        // AkApiParams to hashmap, filter out of null-value
        HashMap<String, String> paramsMap = getRawApiParams2HashMap(annosArr, args);
        // parse '{}'s in url
        invokeUrl = parseUrlbyParams(invokeUrl, paramsMap);
        // cleared hashmap to params, and filter out of the null value
        Iterator<Entry<String, String>> iter = paramsMap.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String, String> entry = iter.next();
            params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        
        // get the signature string if using
        AkSignature akSig = method.getAnnotation(AkSignature.class);
        if (akSig != null) {
            Class<?> clazzSignature = akSig.using();
            if (clazzSignature.getInterfaces().length > 0 // TODO NEED VERIFY
                    && InvokeSignature.class.getName().equals(
                            clazzSignature.getInterfaces()[0].getName())) {
                InvokeSignature is =
                    (InvokeSignature) clazzSignature.getConstructors()[0].newInstance();
                String sigValue = is.signature(akSig, invokeUrl, params);
                String sigParamName = is.getSignatureParamName();
                if (sigValue != null && sigParamName != null
                        && sigValue.length()>0 && sigParamName.length()>0 ) {
                    params.add(new BasicNameValuePair(sigParamName, sigValue));
                } 
            }
        }
        
        // choose POST GET PUT DELETE to use for this invoke
        String retString = "";
        if (akGet != null) {
            StringBuilder sbUrl = new StringBuilder(invokeUrl);
            if (!(invokeUrl.endsWith("?") || invokeUrl.endsWith("&"))) {
                sbUrl.append("?");
            }
            for (NameValuePair nvp : params) {
                sbUrl.append(nvp.getName());
                sbUrl.append("=");
                sbUrl.append(URLEncoder.encode(nvp.getValue(),"UTF-8"));
                sbUrl.append("&");
            } // now default using UTF-8, maybe improved later
            retString = HttpInvoker.get(sbUrl.toString());
        } else if (akPost != null) {
            retString = HttpInvoker.post(invokeUrl, params);
        } else { // use POST for default
            retString = HttpInvoker.post(invokeUrl, params);
        }
        
        Log.d(TAG, retString);
        
        // parse the return-string
        Class<?> returnType = method.getReturnType();
        try {
            return JsonMapper.json2pojo(retString, returnType);
        } catch (JsonProcessingException e) {
            Log.e(TAG, retString, e);  // log can print the error return-string
            throw new AkInvokeException(AkInvokeException.CODE_JSONPROCESS_EXCEPTION,
                    e.getMessage(), e);
        } catch (IOException e) {
            throw new AkInvokeException(AkInvokeException.CODE_IO_EXCEPTION,
                    e.getMessage(), e);
        }
        /*
         * also can use gson like this eg. 
         *  Gson gson = new Gson();
         *  return gson.fromJson(HttpInvoker.post(url, params), returnType);
         */
    }

    /**
     * Replace all the {} block in url to the actual params, 
     * clear the params used in {block}, return cleared params HashMap and replaced url.
     * @param url such as http://server/{namespace}/1/do
     * @param params such as hashmap include (namespace->'mobile')
     * @return the parsed param will be removed in HashMap (params)
     */
    private String parseUrlbyParams(String url, HashMap<String, String> params) {
        StringBuffer sbUrl = new StringBuffer();
        
        Pattern pattern = Pattern.compile("\\{(.+?)\\}");
        Matcher matcher = pattern.matcher(url);
        
        while (matcher.find()) {
            matcher.appendReplacement(sbUrl, params.get(matcher.group(1)));
            params.remove(matcher.group(1));
        }
        matcher.appendTail(sbUrl);
        
        return sbUrl.toString();
    }

    /**
     * AkApiParams to hashmap, filter out of null-value
     * @param annosArr Method's params' annotation array[][]
     * @param args Method's params' values
     * @return HashMap all (paramName -> paramValue)
     */
    private HashMap<String, String> getRawApiParams2HashMap(Annotation[][] annosArr, 
                                                            Object[] args) {
        HashMap<String, String> paramsMap = new HashMap<String, String>();
        for (int idx = 0; idx < args.length; idx++) {
            String paramName = null;
            for (Annotation a : annosArr[idx]) {
                if(AkApiParam.class.equals(a.annotationType())){
                    paramName = ((AkApiParam)a).value();
                }
            }
            if (paramName != null) {
                Object arg = args[idx];
                if (arg != null)        // filter out of null-value param
                    paramsMap.put(paramName, arg.toString());
            }
        }
        return paramsMap;
    }    
}  