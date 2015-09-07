String symbol = params['symbol']
if(symbol == null)	forward '/WEB-INF/pages/index.gtpl'
Stock s = new Stock(symbol)
s.loadData()
s.computeStats()
s.saveDataToDataBase()
request.setAttribute('stock',s)

forward '/WEB-INF/pages/stockT.gtpl'

