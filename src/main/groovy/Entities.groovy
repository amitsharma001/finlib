import groovyx.gaelyk.datastore.*

@Entity(unindexed=false)
class StockData {
    @Key String symbol
    String annual_returns, monthly_returns, daily_returns
    String date_saved
}