
get "/", forward: "/WEB-INF/pages/index.gtpl"
get "/datetime", forward: "/datetime.groovy"
get "/dataStoreTest", forward: "/datestoreGroovlet.groovy"
get "/stock", forward: "/StockGroovlet.groovy"
get "/favicon.ico", redirect: "/images/gaelyk-small-favicon.png"
