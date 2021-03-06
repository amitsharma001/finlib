
//@Grab(group='junit', module='junit', version='4.11')
import org.junit.Test

class StockTests {
    
    @Test
    void loadSerializedData() {
        Stock s = new Stock("VTI")
        File f = new File("src/test/groovy/returns.dat")
        def lines = f.readLines();
        assert lines[0] == "Annual"
        s.stringToIntervalReturn(lines[1],(char)'a')
        assert lines[2] == "Monthly"
		s.stringToIntervalReturn(lines[3],(char)'m')
        assert lines[4] == "Daily"
		s.stringToIntervalReturn(lines[5],(char)'d')

		assert s.annualReturns.size() == 13
		assert s.monthlyReturns.size() == 170
		assert s.dailyReturns.size() == 1000
		

		IntervalReturn[] areturns = s.annualReturns.collect {k,v->v}
		IntervalReturn[] mreturns = s.monthlyReturns.collect {k,v->v}
		IntervalReturn[] dreturns = s.dailyReturns

		assert areturns[0].year == 2014
		assert areturns[-1].year == 2002

		assert mreturns[0].year == 2015
		assert mreturns[0].month == 8

		assert dreturns[0].date == Date.parse("yyyy-MM-dd hh:mm:ss", "2015-08-31 00:00:00")
    }
    
    @Test
    void serailizeData() {
    	Stock s = new Stock("VTI")
        File f = new File("src/test/groovy/returns.dat")
        def lines = f.readLines();
        assert lines[0] == "Annual"
        s.stringToIntervalReturn(lines[1],(char)'a')
        assert lines[2] == "Monthly"
		s.stringToIntervalReturn(lines[3],(char)'m')
        assert lines[4] == "Daily"
		s.stringToIntervalReturn(lines[5],(char)'d')

		assert lines[1] == s.intervalReturnToString(s.annualReturns)
		assert lines[3] == s.intervalReturnToString(s.monthlyReturns)
		assert lines[5] == s.intervalReturnToString(s.dailyReturns)
    }

    @Test
    void getDataFromYahoo() {
        Stock s = new Stock("VTI")
        s.loadDataFromYahoo()
        assert s.dailyReturns.size() > 1100
        assert s.annualReturns.size() > 10
        assert s.monthlyReturns.size() > 120
    }

    @Test
    void encodeDates() {
    	def testMonths = [[12,2000],[1,2001],[5,2007],[2,1990]]
    	testMonths.each {
	    	IntervalReturn ret = new IntervalReturn(it[0], it[1], 0.0)
	    	int encoded = ret.getEncoded()
	    	IntervalReturn ret1 = new IntervalReturn(encoded,0.0,(char)'m')
	    	assert ret.year == ret1.year
	    	assert ret.month == ret1.month
    	}
    }
}