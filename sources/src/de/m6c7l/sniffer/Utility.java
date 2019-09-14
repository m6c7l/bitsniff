/*
 * Copyright (c) 2018, Manfred Constapel
 * This file is licensed under the terms of the MIT license.
 */

package de.m6c7l.sniffer;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utility {

    private Utility() {}
    
    // --- file ---

    // convert url to uri
    public static URI uri(URL url) throws MalformedURLException, URISyntaxException {
        URI uri = url.toURI();
        if (uri.getAuthority() != null && uri.getAuthority().length() > 0) { // Hack for Windows UNC Path
            uri = (new URL("file://" + url.toString().substring("file:".length()))).toURI();
        }
        return uri;
    }

    // get path to jar file
    public static URL jar(Class<?> aclass) throws UnsupportedEncodingException, MalformedURLException {
        CodeSource codeSource = aclass.getProtectionDomain().getCodeSource();
        URL url = null;
        if (codeSource.getLocation() != null) {
            url = codeSource.getLocation();
        } else {
            String path = aclass.getResource(aclass.getSimpleName() + ".class").getPath();
            path = URLDecoder.decode(path, "UTF-8");
            path = path.substring(0, path.indexOf(aclass.getName().replaceAll("\\.", "/")));
            url = new URL("file:" + path);
        }
        return url;
    }

    // --- libs ---

    public static void add_lib_path(String s) throws IOException {
        try {
            // this enables the java.library.path to be modified at runtime
            Field field = ClassLoader.class.getDeclaredField("usr_paths");
            field.setAccessible(true);
            String[] paths = (String[]) field.get(null);
            for (int i = 0; i < paths.length; i++) {
                if (s.equals(paths[i]))
                    return;
            }
            String[] tmp = new String[paths.length + 1];
            System.arraycopy(paths, 0, tmp, 0, paths.length);
            tmp[paths.length] = s;
            field.set(null, tmp);
            System.setProperty("java.library.path", System.getProperty("java.library.path") + File.pathSeparator + s);
        } catch (IllegalAccessException e) {
            throw new IOException(e.getMessage());
        } catch (NoSuchFieldException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    // --- args ---
    
    public static Object[] parse(String[] args) {
        final Map<String, List<String>> params = new HashMap<>();
        List<String> options = null;
        for (int i = 0; i < args.length; i++) {
            final String a = args[i];
            if (a.charAt(0) == '-') {
            //    if (a.length() < 2) {
            //        throw new IllegalArgumentException("error at argument " + a);
            //    }
                options = new ArrayList<>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            //} else {
            //    throw new IllegalArgumentException("illegal parameter usage");
            }
        }
        Object[] result = new Object[] {null, null};        
        if ((params.get("p") != null) && (params.get("p").size() == 1)) {
            result[0] = params.get("p").get(0);
        }
        if ((params.get("c") != null) && (params.get("c").size() == 1)) {
            result[1] = params.get("c").get(0);
        }     
        return result;
    }
    
}
