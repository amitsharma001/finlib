public class IntervalReturn {
    public static Date offset = Date.parse("yyyy-MM-dd hh:mm:ss", "2000-01-01 00:00:00")
    int year = -1, month = -1
    Date date = null
    double theReturn

    IntervalReturn(Date date, double dreturn) {
        this.date = date
        this.theReturn = dreturn
    }
    IntervalReturn( int month, int year, double mreturn) {
        this.year = year
        this.month = month 
        this.theReturn = mreturn
    }
    IntervalReturn(int year, double areturn) {
        this.year = year
        this.theReturn = areturn
    }
    IntervalReturn(int encodedDate, double treturn, char type) {
        this.theReturn = treturn
        setDecoded(encodedDate, type)
    }
    void setDecoded(int encodedDate, char type) {
        if(type == 'a') this.year = encodedDate + 2000
        if(type == 'm') {
            year = (int)(encodedDate / 12) + 2000
            month = encodedDate % 12
            if(month <= 0) {
                year -= 1
                month += 12
            }
        } 
        if(type == 'd') {
            this.date = IntervalReturn.offset.plus (encodedDate)
        }        
    }
    int getEncoded() {
        if(date != null) return getEncoded(date)
        if(month != -1) return getEncoded(month, year)
        else if(year != -1) return getEncoded(year)
    }
    String toString() {
        if(date != null) return String.format("Date: %s, Days since 2000: %d, Return: %.4f",date.format("MM/dd/yy"),getEncoded(),theReturn)
        if(month != -1) return String.format("%d %2d/%-12d: %8.2f %s",getEncoded(),month,year,theReturn,"%")
        if(year != -1) return String.format("%-12d: %8.2f %s",year,theReturn,"%")
        return "No date associated with return"
    }
    static getEncoded(Date d) {
        return d.minus(IntervalReturn.offset)
    }
    static getEncoded(int month, int year) {
        return (year - 2000)*12 + month
    }
    static getEncoded(int year) {
        return year - 2000
    }
}
