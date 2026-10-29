package backend.models;

/**
 * This class represents a sales record per hour (x report), including the hour and the quantity sold.
 * @author Ashley Hoang
 */
public final class XRecord {
    private final String hour;
    private final long quantity;
    private final double revenue;

    /**
     * Creates an hourly X-report record.
     *
     * @param hour     the hour represented by the record
     * @param quantity the number of orders during the hour
     * @param revenue  the revenue during the hour
     * @author Ashley Hoang
     */
    public XRecord(String hour, long quantity, double revenue) {
        this.hour = hour;
        this.quantity = quantity;
        this.revenue = revenue;
    }

    //getter functions
    /**
     * Returns the represented hour.
     *
     * @return the represented hour
     * @author Ashley Hoang
     */
    public String getHour(){
        return hour;
    }
    /**
     * Returns the number of orders during the hour.
     *
     * @return the number of orders during the hour
     * @author Ashley Hoang
     */
    public long getQuantity(){
        return quantity;
    }
    /**
     * Returns the revenue during the hour.
     *
     * @return the revenue during the hour
     * @author Ashley Hoang
     */
    public double getRevenue(){
        return revenue;
    }
}
