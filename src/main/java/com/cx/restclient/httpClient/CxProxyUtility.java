package com.cx.restclient.httpClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import com.cx.restclient.exception.CxClientException;

/**
 * Handles configuring Proxy for Checkmarx server connections
 * 
 * @author randy@checkmarx.com
 * @since 08/2/18
 */
public class CxProxyUtility {
    
    private static final ProxySelector DEFAULT_PROXY = ProxySelector.getDefault();
    
    /**
     * Call before initializing HttpClient.  Compatible with no proxy, system default proxy, or custom proxy.
     * 
     * @param cxUrl CxSAST url to proxy 
     * @param useProxy if true, custom proxy will be configured
     * @param proxyHost proxyHost, no protocol
     * @param proxyPort proxyPort, optional
     * @param logger
     */
    public static void configureProxy(String cxUrl,
                boolean useProxy, String proxyHost, Integer proxyPort,
                Logger logger) throws CxClientException {
        if (useProxy) {
            if (StringUtils.isEmpty(proxyHost))
                throw new IllegalArgumentException("Proxy host cannot be empty");
            
            final ProxySelector cxProxy = new CxProxySelector(
                    DEFAULT_PROXY,
                    cxUrl,
                    proxyHost,
                    (proxyPort == null) ? 80 : proxyPort,
                    logger
            );
            ProxySelector.setDefault(cxProxy);
            logger.info("Checkmarx proxy configured");
        } else {
            // set default proxy in case Cx proxy was removed.
            setDefaultProxy(logger);
        }
    }

    private static void setDefaultProxy(Logger logger) {
            logger.info("No proxy configured for Checkmarx, using System default");
            ProxySelector.setDefault(DEFAULT_PROXY);
    }
    
    public static class CxProxySelector extends ProxySelector {
        
        private transient Logger logger;
        
        private final ProxySelector defaultProxy;
        private final URL cxUrl;
        private final String proxyHost;
        private final int proxyPort;
        private final List<Proxy> proxyList = new ArrayList<Proxy>();
        private final Proxy cxProxy;
        
        public CxProxySelector(String cxUrl,
                String proxyHost, int proxyPort, 
                Logger logger) throws CxClientException {
            this(ProxySelector.getDefault(), cxUrl, proxyHost, proxyPort, logger);
        }

        public CxProxySelector(ProxySelector defaultProxy,
                String cxUrl,
                String proxyHost, int proxyPort, 
                Logger logger) throws CxClientException {
            
            this.defaultProxy = defaultProxy;
            this.cxUrl = initUrl(cxUrl);
            this.cxProxy = initProxy(proxyHost, proxyPort);
            this.proxyList.add(cxProxy);
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
            this.logger = logger;
            
            logger.info(String.format("Configuring proxy for Checkmarx: url=%s; proxy=%s:%d", 
                    cxUrl, proxyHost, proxyPort));
        }
        
        private URL initUrl(String cxUrl) throws CxClientException {
            try {
                return new URL(cxUrl);
            } catch (MalformedURLException ex) {
                final String msg = "Checkmarx server url is malformed.  Correct and retry.";
                logger.error(msg, ex);
                throw new CxClientException(msg);
            }
        }

        private Proxy initProxy(String proxyHost, int proxyPort) throws CxClientException {
            
            try {
                final InetAddress addr = InetAddress.getByName(proxyHost);
                final SocketAddress sa = new InetSocketAddress(addr, proxyPort);
                return new Proxy(Type.HTTP, sa);
            } catch (Exception ex) {
                final String msg = "Proxy address is malformed.  Correct and retry.";
                logger.error(msg, ex);
                throw new CxClientException(msg);
            }
        }
        
        @Override
        public List<Proxy> select(URI uri) {
            if (uri == null) {
                throw new IllegalArgumentException("URI can't be null.");
            }
            logger.info("Selecting proxy for uri: " + uri.toString());
            
            if (uriMatchesCx(uri)) {
                logger.info(String.format("Using proxy for Checkmarx: %s:%d", proxyHost, proxyPort));
                return proxyList;
            }
            return defaultProxy.select(uri);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            final String msg = 
                    String.format("Connection could not be established to Checkmarx proxy: %s:%d",
                            this.proxyHost, this.proxyPort);
            logger.error(msg, ioe);
            throw new RuntimeException(msg, ioe);
        }

        private boolean uriMatchesCx(URI uri) {
            return uri.getHost().equalsIgnoreCase(cxUrl.getHost());
        }

    }
    
}
