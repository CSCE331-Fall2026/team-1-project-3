#include <exception>
#include <iostream>
#include <stdexcept>
#include <memory>
#include <random>
#include <vector>
#include <algorithm>
#include <ctime>
#include <fstream>
#include "common.h"

using namespace std;

// BOUNDS
#define MIN_AMOUNT_OF_ENTREES 1 // default: 1
#define MAX_AMOUNT_OF_ENTREES 2 // default: 2
#define MIN_AMOUNT_OF_SIDES 1 // default: 1
#define MAX_AMOUNT_OF_SIDES 2 // default: 2
#define MIN_AMOUNT_OF_DRINKS 1 // default: 1
#define MAX_AMOUNT_OF_DRINKS 2 // default: 2

#define MIN_ORDERS_PER_DAY 200 // default: 200
#define MAX_ORDERS_PER_DAY 300 // default: 300

// How many cashiers each panda express has.
#define EMPLOYEES_PER_LOCATION 6 // default: 3

// How many managers each panda express has.
#define MANAGERS_PER_LOCATION 1 // default: 1

// How many hours a cashier works before being rotated out.
#define EMPLOYEE_WORK_SHIFT 7 // default: 7

// Whether or not to make the script somewhat nice looking (false), or fast (true)
#define FAST_MODE true // default: true

// DAYS
#define LENGTH_IN_DAYS 7 * 52 //default: 7 * 52

// Peak days double the number of orders on that day.
#define NUMBER_OF_PEAK_DAYS 2 // default: 2

#define OPENING_TIME_UNIX 3'600 * 7 // opens at 7:00 am
#define CLOSING_TIME_UNIX 3'600 * 21 // closes at 21:00 or 9:00 pm

#define PEAK_LUNCH_TIME_UNIX 3'600 * 13 // peak lunch time is 1:00 pm
#define PEAK_DINNER_TIME_UNIX 3'600 * 18 // peak dinner time is 6:00 pm

// DONT TOUCH THESE
#define WORKSHIFT_SECONDS 3'600 * EMPLOYEE_WORK_SHIFT
const int MIN_BY_CATEGORY[CATEGORY_LENGTH] = {MIN_AMOUNT_OF_ENTREES, MIN_AMOUNT_OF_SIDES, MIN_AMOUNT_OF_DRINKS};
const int MAX_BY_CATEGORY[CATEGORY_LENGTH] = {MAX_AMOUNT_OF_ENTREES, MAX_AMOUNT_OF_SIDES, MAX_AMOUNT_OF_DRINKS};
#define MIN_ITEMS_PER_ORDER MIN_AMOUNT_OF_ENTREES + MIN_AMOUNT_OF_SIDES + MIN_AMOUNT_OF_DRINKS
#define MAX_ITEMS_PER_ORDER MAX_AMOUNT_OF_ENTREES + MAX_AMOUNT_OF_SIDES + MAX_AMOUNT_OF_DRINKS

int main() {
    try {
    //The timespan will be LENGTH_IN_DAYS days before now to now.
    time_t startTime = time(nullptr) - 86'400 * 30;
    tm* timeinfo = std::localtime(&startTime);
    startTime = startTime - (timeinfo->tm_hour * 3'600 + timeinfo->tm_min * 60 + timeinfo->tm_sec);
    unique_ptr<Generator> g = make_unique<Generator>(startTime, NUMBER_OF_PEAK_DAYS);

    g->generateNDays(LENGTH_IN_DAYS);

    const char* filename = "../sql/Script.sql";
    if (FAST_MODE)
        g->writeAllFast(filename);
    else
        g->writeAll(filename);

    g->writeReset("../sql/Reset.sql");
    g->writeTeardown("../sql/Teardown.sql");

    cout << "Generation completed successfully!" << endl;

    return 0;
    } catch (const exception& e) {
        cerr << "Generator failed: " << e.what() << endl;
        return 1;
    }
}

//Init currentId before it is used.
    int Scriptable::currentId = 0;

//Init the global time buffer.
    char Generator::timeBuf[128];

// SQL CONVERT FUNCTIONS

static string escapeSql(const string& value) {
    string result;
    result.reserve(value.size());

    for (char c : value) {
        if (c == '\'')
            result += "''";
        else
            result += c;
    }

    return result;
}

string Location::toSql() const noexcept {
    return "INSERT INTO location (location_id, name, city, state)";
}
string Location::toVals() const noexcept {
    string outp{"("};

    outp += std::to_string(id);
    outp += ", '";
    outp += escapeSql(name);
    outp += "', '";
    outp += escapeSql(city);
    outp += "', '";
    outp += escapeSql(state);
    outp += '\'';

    return outp + ")";
}

string User::toSql() const noexcept {
    return "INSERT INTO app_user (user_id, name, password, perms)";
}
string User::toVals() const noexcept {
    string outp{"("};

    outp += std::to_string(userId);
    outp += ", '";
    outp += escapeSql(name);
    outp += "', '";
    outp += escapeSql(password);
    outp += "', '";
    outp += to_string(perm);
    outp += '\'';


    return outp + ")";
}

string Item::toSql() const noexcept {
    return "INSERT INTO item (item_id, category_id, price, name, active_from, active_to)";
}
string Item::toVals() const noexcept {
    string outp{"("};

    outp += std::to_string(id);
    outp += ", ";
    outp += std::to_string(category + 1);
    outp += ", ";
    outp += std::to_string(price);
    outp += ", ";
    outp += '\'' + escapeSql(name) + '\'';
    outp += ", '" + escapeSql(startTime.toString()) + "', '" + escapeSql(endTime.toString()) + '\'';

    return outp + ")";
}

string Ingredient::toSql() const noexcept {
    return "INSERT INTO inventory (inventory_id, name, stock, min_stock, next_shipment, shelf_life)";
}
string Ingredient::toVals() const noexcept {
    string outp{"("};

    outp += std::to_string(id);
    outp += ", '";
    outp += escapeSql(name);
    outp += "', ";
    outp += std::to_string(stock);
    outp += ", ";
    outp += std::to_string(minStock);
    outp += ", '";

    char buf[40];
    std::tm* timepoint = std::localtime(&nextShipment);
    std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", timepoint);

    outp += buf;
    outp += "', '";

    timepoint = std::localtime(&shelfLife);
    std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", timepoint);

    outp += buf;
    outp += '\'';

    return outp + ')';
}

string IngredientRelation::toSql() const noexcept {
    return "INSERT INTO ingredient_list (item_id, inventory_id)";
}
string IngredientRelation::toVals() const noexcept {
    string outp{"("};

    outp += std::to_string(itemId);
    outp += ", ";
    outp += std::to_string(ingredientId);

    return outp + ')';
}

string OrderObject::toSql() const noexcept {
    return "INSERT INTO receipt_item (receipt_item_id, receipt_id, item_id, quantity, price_at_sale)";
}
string OrderObject::toVals() const noexcept {
    string outp{"("};

    outp += std::to_string(id);
    outp += ", ";
    outp += std::to_string(parentId);
    outp += ", ";
    outp += std::to_string(itemId);
    outp += ", ";
    outp += std::to_string(amount);
    outp += ", ";
    outp += std::to_string(amount * item->getPrice());
    
    return outp + ')';
}

string Order::toSql() const noexcept {
    return "INSERT INTO receipt (receipt_id, location_id, user_id, receipt_timestamp)";
}
string Order::toVals() const noexcept {
    string outp{"("};

    outp += std::to_string(id);
    outp += ", ";
    outp += std::to_string(locationId);
    outp += ", ";
    outp += std::to_string(userId);
    outp += ", '";

    char buf[40];
    std::tm* timepoint = std::localtime(&timestamp);
    std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", timepoint);

    outp += buf;
    outp += "')";

    return outp;
}
string Order::toFullSql() const noexcept {
    string outp{toSql() + " VALUES " + toVals() + ';'};
    outp.reserve(64 + 192 * items.size());

    if (items.empty())
        return outp;

    outp += "\n\t" + items[0]->toSql() + " VALUES\n";

    for (int i = 0, size = items.size(); i < size; ++i)
        outp += "\t\t" + items[i]->toVals() + (i == size - 1 ? ";\n" : ",\n");

    return outp;
}

string Timetable::toSql() const noexcept {
    return "INSERT INTO user_timetable (timetable_id, user_id, location_id, clock_in_time, clock_out_time)";
}
string Timetable::toVals() const noexcept {
    string outp{"("};

    outp += std::to_string(id);
    outp += ", ";
    outp += std::to_string(userId);
    outp += ", ";
    outp += std::to_string(locationId);
    outp += ", '";

    char buf[40];
    std::tm* timepoint = std::localtime(&clockIn);
    std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", timepoint);

    outp += buf;
    outp += "', '";

    timepoint = std::localtime(&clockOut);
    std::strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", timepoint);

    outp += buf;

    return outp + "')";
}

// MISC FUNCTIONS

ostream& operator<<(ostream& os, const Scriptable& obj) {
    os << obj.toFullSql();
    return os;
}
string Scriptable::toSql() const noexcept {
    return "";
}
string Scriptable::toVals() const noexcept {
    return "";
}
string Scriptable::toFullSql() const noexcept {
    return toSql() + " VALUES " + toVals() + ';';
}

int Location::getId() const noexcept {
    return id;
}

int User::getUserId() const noexcept {
    return userId;
}

SeasonTime SeasonTime::fromTimepoint(const tm *point) noexcept {
    // plus 1 to keep month 1 indexed instead of 0 indexed
    return {(uint8_t)(point->tm_mon + 1), (uint8_t)point->tm_mday};
}

string SeasonTime::toString() const noexcept {
    char outp[6] = {(char)(month / 10 + '0'), (char)(month % 10 + '0'),'-', (char)(day / 10 + '0'), (char)(day % 10 + '0'), (char)NULL };
    return outp;
}

ostream& operator<<(ostream& os, const SeasonTime& time) {
    os << time.toString();
    return os;
}

bool operator==(const SeasonTime& lhs, const SeasonTime& rhs) noexcept {
    return lhs.month == rhs.month && lhs.day == rhs.day;
}
bool operator>(const SeasonTime& lhs, const SeasonTime& rhs) noexcept {
    return lhs.month > rhs.month || (lhs.month == rhs.month && lhs.day > rhs.day);
}
bool operator<(const SeasonTime& lhs, const SeasonTime& rhs) noexcept {
    return lhs.month < rhs.month || (lhs.month == rhs.month && lhs.day < rhs.day);
}
bool operator>=(const SeasonTime& lhs, const SeasonTime& rhs) noexcept {
    return lhs.month > rhs.month || (lhs.month == rhs.month && lhs.day >= rhs.day);
}
bool operator<=(const SeasonTime& lhs, const SeasonTime& rhs) noexcept {
    return lhs.month < rhs.month || (lhs.month == rhs.month && lhs.day <= rhs.day);
}

int Item::getId() const noexcept {
    return id;
}

float Item::getPrice() const noexcept {
    return price;
}

ItemCategory Item::getCategory() const noexcept {
    return category;
}

const SeasonTime& Item::getStartTime() const noexcept {
    return startTime;
}

const SeasonTime& Item::getEndTime() const noexcept {
    return endTime;
}

void OrderObject::setParentId(int id) {
    parentId = id;
}

Item*& OrderObject::getItem() noexcept {
    return item;
}

int& OrderObject::getItemId() noexcept {
    return itemId;
}

int& OrderObject::getAmount() noexcept {
    return amount;
}

void Order::setChildIds() {
    for (size_t i = 0; i < items.size(); ++i) {
        if (items[i] == nullptr) {
            cout << "WARNING: ITEM #" << std::to_string(i) << " IS NULL!" << endl;
            continue;
        }

        items[i]->setParentId(id);
    }
}

const vector<unique_ptr<OrderObject>>& Order::getItems() const noexcept {
    return items;
}

time_t Order::getTimestamp() const noexcept {
    return timestamp;
}

// GENERATOR FUNCTIONS

void Generator::setupRandomGenerators() {
    percentGen = uniform_real_distribution<double>(0.0, 1.0);

    itemsInOrder = uniform_int_distribution<int>(MIN_ITEMS_PER_ORDER, MAX_ITEMS_PER_ORDER);
    ordersPerDay = uniform_int_distribution<int>(MIN_ORDERS_PER_DAY, MAX_ORDERS_PER_DAY);

    lunchOrderTimes = normal_distribution<float>(PEAK_LUNCH_TIME_UNIX, 3'600 * 2);
    dinnerOrderTimes = normal_distribution<float>(PEAK_DINNER_TIME_UNIX, 3'600 * 1);
    orderChoice = bernoulli_distribution(0.65f);

    cout << "The peak days are on ";
    int prev = 0;
    for (size_t i = 0; i < peakDays.size(); ++i) {
        if (peakDays.size() > 2 && i != 0 && i != peakDays.size() - 1)
            cout << ", ";
        if (i == peakDays.size() - 1)
            cout << " and ";
        peakDays[i] = std::round(percentGen(rngSeed) * (LENGTH_IN_DAYS - prev - peakDays.size() + i)) + prev;
        if (peakDays[i] == prev)
            ++(peakDays[i]);
        prev = peakDays[i];
        cout << "day " << std::to_string(prev + 1);
    }
    cout << '.' << endl;
}

unique_ptr<OrderObject> Generator::generateOrderObject(int counts[]) {
    int unavailable = unavailableItems.size();
    int itemId = round(percentGen(rngSeed) * (allItems.size() - 1 - unavailable)) + 1;

    for (int i = 0; i < unavailable; ++i)
        if (unavailableItems[i] <= itemId)
            ++itemId;
        else break;

    Item* itemPointer = allItems[itemId - 1].get(); 
    ItemCategory category = itemPointer->getCategory();

    int max = MAX_BY_CATEGORY[category] - counts[category];

    int amount = max <= 1 ? 1 : std::round(percentGen(rngSeed) * (max - 1)) + 1;

    return std::make_unique<OrderObject>(itemPointer->getId(), amount, itemPointer);
}

void Generator::regenerateOrderObject(OrderObject* order, int count, ItemCategory category) {
    int unavailable = unavailableItemsByCategory[category];
    int itemIndex = std::round(percentGen(rngSeed) * (itemByCategory[category].size() - (1 + unavailable)));

    Item *item = itemByCategory[category][itemIndex].get();
    int itemId = item->getId();

    int skipIndex = 0;
    for (uint8_t i = 0; i < category; ++i)
        skipIndex += unavailableItemsByCategory[i];
    for (int i = 0; i < unavailable; ++i)
        if (unavailableItems[skipIndex + i] <= itemId)
            ++itemId;
        else break;

    item = allItems[itemId - 1].get();

    order->getItemId() = item->getId();
    order->getItem() = item;

    int max = MAX_BY_CATEGORY[category] - count;
    order->getAmount() = max <= 1 ? 1 : std::round(percentGen(rngSeed) * (max - 1)) + 1;
}

bool Generator::validateOrderItemWithTime(const Item* item, const time_t& time) noexcept {
    const SeasonTime& start = item->getStartTime();
    const SeasonTime& end = item->getEndTime();
    
    tm* timepoint = std::localtime(&time);
    int month = timepoint->tm_mon + 1;
    int day = timepoint->tm_mday;

    bool outp = (month > start.month || (month == start.month && day >= start.day)) &&
        (month < end.month || (month == end.month && day <= end.day));

    return outp;
}

bool Generator::validateOrderItem(const Item* item) const noexcept {
    return validateOrderItemWithTime(item, currentDay);
}

unique_ptr<Order> Generator::generateOrder(time_t timeOfOrder) {

    int numOfItems = itemsInOrder(rngSeed);
    vector<unique_ptr<OrderObject>> items;
    items.reserve(numOfItems);

    bool canExit = false;
    int counts[CATEGORY_LENGTH]{};
    int itemCount = 0;
    for (int i = 0; i < numOfItems || !canExit; ++i) {
        unique_ptr<OrderObject> obj = generateOrderObject(counts);
        int retries = 0;

    retry:
        Item* item = obj->getItem();
        ItemCategory category = item->getCategory();

        int itemId = item->getId();
        int count = counts[category];
        int max = MAX_BY_CATEGORY[category];

        if (count >= max) {
            category = static_cast<ItemCategory>(category + 1 == CATEGORY_LENGTH ? 0 : category + 1);
            regenerateOrderObject(obj.get(), counts[category], category);

            if (++retries >= CATEGORY_LENGTH) {
                break;
            }

            goto retry;
        }
        else if (count + obj->getAmount() > max)
            obj->getAmount() = max - count;

        int amount = obj->getAmount();

        // If the amount is above 1, shift the for loop to account for this
        if (amount > 1)
            i += amount - 1;

        itemCount += amount;
        counts[category] += amount;

        //This is O(n^2), but each order will never contain a large amount of items so it should be fine.
        for (size_t j = 0; j < items.size(); ++j) {
            if (items[j]->getItem()->getId() == itemId) {
                items[j]->getAmount() += amount;
                obj.reset();
                break;
            }
        }

        if (obj != nullptr)
            items.push_back(std::move(obj));

        if (!canExit) {
            bool failed = false;

            for (int i = 0; i < CATEGORY_LENGTH; ++i)
                if (counts[i] < MIN_BY_CATEGORY[i]) {
                    failed = true;
                    break;
                }

            if (!failed)
                canExit = true;
        }
    }

    // Very ugly, but important. Makes sure to evenly remove items if there are more items than allowed.
    if (itemCount > numOfItems) {
        int overAmount = itemCount - numOfItems;
        for (int i = 0; i < CATEGORY_LENGTH && overAmount > 0; ++i) {
            if (counts[i] <= MIN_BY_CATEGORY[i])
                continue;

            for (int j = items.size() - 1; j >= 0 && overAmount > 0; --j) {
                if (items[j]->getItem()->getCategory() != i) 
                    continue;

                if (items[j]->getAmount() > 1)
                    --(items[j]->getAmount());
                else {
                    std::swap(items[j], items.back());
                    items.pop_back();
                }

                --overAmount;
            }                    
        }
    }

    time_t timestamp = timeOfOrder;
    unique_ptr<Order> o = std::make_unique<Order>(currentUser->getUserId(), currentLocation->getId(), std::move(items), timestamp);

    if (timestamp > clockOutTime) {
        userTimetables.push_back(std::make_unique<Timetable>(currentUser->getUserId(), currentLocation->getId(), clockInTime, clockOutTime));
        clockInTime = clockOutTime;
        clockOutTime += WORKSHIFT_SECONDS; 
        currentUser = currentUser->getUserId() == (int)allUsers.size() ? allUsers[0].get() : allUsers[currentUser->getUserId()].get();
    }

    return o;
}

void Generator::incNDaysOfTime(time_t &time, int days) noexcept {
    tm *tp = localtime(&time);

    int hourDelta = 12 - tp->tm_hour;
    if (hourDelta > 0)
        tp->tm_hour += hourDelta;

    tp->tm_mday += days; // Increment days
    tp->tm_isdst = -1; // auto check if dst.

    time = mktime(tp);

    if (hourDelta > 0)
        time -= 3'600 * hourDelta;
}

void Generator::incDayOfTime(time_t &time) noexcept {
    incNDaysOfTime(time, 1);
}

void Generator::generateNextDay() {

    // const int dayLength = CLOSING_TIME_UNIX - OPENING_TIME_UNIX;

    size_t numOfOrders = ordersPerDay(rngSeed);
    int totalOrders = orders.size();

    if (current < peakDays.size() && peakDays[current] <= totalOrders) {
        numOfOrders *= 2;
        ++current;
    }

    checkNextDayItemAvailability();

    vector<unique_ptr<Order>> ordersToday;
    ordersToday.reserve(numOfOrders);

    vector<time_t> orderTimes(numOfOrders);

    for (size_t i = 0; i < numOfOrders; ++i) {
        time_t localOrderTime = min(max((int)round(orderChoice(rngSeed) ? lunchOrderTimes(rngSeed) : dinnerOrderTimes(rngSeed)), OPENING_TIME_UNIX), CLOSING_TIME_UNIX);
        orderTimes[i] = currentDay + localOrderTime;
    }

    std::sort(orderTimes.begin(), orderTimes.end());

    clockInTime = currentDay + OPENING_TIME_UNIX;
    clockOutTime = currentDay + WORKSHIFT_SECONDS + OPENING_TIME_UNIX;

    for (size_t i = 0; i < numOfOrders; ++i)
        ordersToday.push_back(std::move(generateOrder(orderTimes[i])));
    
    // 30 min = 1,800 seconds
    if (currentDay - clockInTime >= 1'800) {
        userTimetables.push_back(std::make_unique<Timetable>(currentUser->getUserId(), currentLocation->getId(), clockInTime, clockOutTime));
        currentUser = currentUser->getUserId() == (int)allUsers.size() ? allUsers[0].get() : allUsers[currentUser->getUserId()].get();
    }

    incDayOfTime(currentDay);

    orders.push_back(std::move(ordersToday));
}

void Generator::generateNDays(int days) {
    for (int i = 0; i < days; ++i)
        generateNextDay();
}

void Generator::checkNextDayItemAvailability() {
    checkNDaysItemAvailability(1);
}

void Generator::checkNDaysItemAvailability(int days) {
    const SeasonTime startTime = SeasonTime::fromTimepoint(localtime(&currentDay)); 

    time_t endDay = currentDay;
    incNDaysOfTime(endDay, days);
    const SeasonTime endTime = SeasonTime::fromTimepoint(localtime(&endDay));

    time_t previousDay = currentDay;
    incNDaysOfTime(previousDay, -1);
    const SeasonTime startTimeOffset = SeasonTime::fromTimepoint(localtime(&previousDay));

    time_t endDayOffset = currentDay;
    incNDaysOfTime(endDayOffset, days - 1);
    const SeasonTime endTimeOffset = SeasonTime::fromTimepoint(localtime(&endDayOffset));

    const bool swappedTimes = endTime < startTime;
    const bool offsetSwappedTimes = endTimeOffset < startTimeOffset;

    for (size_t i = 0; i < allItems.size(); ++i) {
        const Item *item = allItems[i].get();
        const SeasonTime& start = item->getStartTime();
        const SeasonTime& end = item->getEndTime();

        const bool itemStarted = swappedTimes ?
                                    start >= startTime || start < endTime :
                                    start >= startTime && start < endTime;
        const bool itemEnded   = offsetSwappedTimes ?
                                    end >= startTimeOffset || end < endTimeOffset :
                                    end >= startTimeOffset && end < endTimeOffset;

        if (itemStarted && itemEnded)
            continue; // If both events happen, they cancel each other out.

        if (itemStarted) {
            const int id = item->getId();
            auto it = lower_bound(unavailableItems.begin(), unavailableItems.end(), id);
            if (it != unavailableItems.end() && *it == id)
                unavailableItems.erase(it);
            --unavailableItemsByCategory[item->getCategory()];
        }
        if (itemEnded) {
            const int id = item->getId();
            auto it = lower_bound(unavailableItems.begin(), unavailableItems.end(), id);
            unavailableItems.insert(it, id);
            ++unavailableItemsByCategory[item->getCategory()];
        }
    }
}

void Generator::checkInitialAvailability() noexcept {
     tm *tp = localtime(&currentDay);
    const int startMonth = tp->tm_mon + 1; // plus 1 to keep month 1 indexed instead of 0 indexed
    const int startDay = tp->tm_mday;

    for (size_t i = 0; i < allItems.size(); ++i) {
        const Item *item = allItems[i].get();
        const SeasonTime& start = item->getStartTime();
        const SeasonTime& end = item->getEndTime();
        
        const bool startBehindNow = start.month < startMonth || (start.month == startMonth && start.day <= startDay);
        const bool endInfrontNow = end.month > startMonth || (end.month == startMonth && end.day > startDay);
        const bool startEndSwapped = start.month > end.month || (start.month == end.month && start.day > end.day);

        const bool started = (startBehindNow && endInfrontNow) || (startEndSwapped && (endInfrontNow || startBehindNow));
        // (a && b) || (c && (a || b)) = (a && b) || (c && a) || (c && b)

        if (!started) {
            unavailableItems.push_back(item->getId());
            ++unavailableItemsByCategory[item->getCategory()];
        }
    }
}

template <typename T>
void Generator::writeFast(ofstream& stream, const vector<unique_ptr<T>>& arr) const {
    if (arr.empty())
        return;

    stream << arr[0]->toSql() << " VALUES ";

    for (size_t i = 0; i < arr.size(); ++i)
        stream << arr[i]->toVals() << (i == arr.size() - 1 ? ';' : ',');
}
template <typename T>
void Generator::writeFast(ofstream& stream, const vector<shared_ptr<T>>& arr) const {
    if (arr.empty())
        return;

    stream << arr[0]->toSql() << " VALUES ";

    for (size_t i = 0; i < arr.size(); ++i)
        stream << arr[i]->toVals() << (i == arr.size() - 1 ? ';' : ',');
}

static void writeIdentitySequenceSync(ofstream& stream) {
    stream << "\n"
           << "WITH table_state AS (SELECT MAX(receipt_id) AS max_id FROM receipt), "
           << "sequence_state AS (SELECT last_value FROM pg_sequences WHERE schemaname = current_schema() "
           << "AND sequencename = 'receipt_receipt_id_seq') "
           << "SELECT setval(pg_get_serial_sequence('receipt', 'receipt_id'), "
           << "GREATEST(COALESCE(table_state.max_id, 0), COALESCE(sequence_state.last_value, 1)), "
           << "table_state.max_id IS NOT NULL OR sequence_state.last_value IS NOT NULL) "
           << "FROM table_state CROSS JOIN sequence_state;\n"
           << "WITH table_state AS (SELECT MAX(receipt_item_id) AS max_id FROM receipt_item), "
           << "sequence_state AS (SELECT last_value FROM pg_sequences WHERE schemaname = current_schema() "
           << "AND sequencename = 'receipt_item_receipt_item_id_seq') "
           << "SELECT setval(pg_get_serial_sequence('receipt_item', 'receipt_item_id'), "
           << "GREATEST(COALESCE(table_state.max_id, 0), COALESCE(sequence_state.last_value, 1)), "
           << "table_state.max_id IS NOT NULL OR sequence_state.last_value IS NOT NULL) "
           << "FROM table_state CROSS JOIN sequence_state;\n";
}

static void writeLoaderStart(ofstream& stream) {
    stream << "\\set ON_ERROR_STOP on\n"
           << "BEGIN;\n"
           << "LOCK TABLE category, item, inventory, ingredient_list, receipt, receipt_item, "
           << "location, app_user, user_timetable IN ACCESS EXCLUSIVE MODE;\n";
}

static void writeLoaderEnd(ofstream& stream) {
    stream << "COMMIT;\n";
}

static void writeDestructiveGuard(ofstream& stream) {
    stream << "\\set ON_ERROR_STOP on\n"
           << "DO $$\n"
           << "BEGIN\n"
           << "    IF current_setting('app.allow_destructive', true) IS DISTINCT FROM 'true' THEN\n"
           << "        RAISE EXCEPTION 'Destructive script requires SET app.allow_destructive = true';\n"
           << "    END IF;\n"
           << "END\n"
           << "$$;\n";
}

void Generator::writeTruncates(ofstream& stream) const {
    stream << "TRUNCATE TABLE category CASCADE;\n";
    stream << "TRUNCATE TABLE item CASCADE;\n";
    stream << "TRUNCATE TABLE inventory CASCADE;\n";
    stream << "TRUNCATE TABLE ingredient_list CASCADE;\n";
    stream << "TRUNCATE TABLE receipt CASCADE;\n";
    stream << "TRUNCATE TABLE receipt_item CASCADE;\n";
    stream << "TRUNCATE TABLE location CASCADE;\n";
    stream << "TRUNCATE TABLE app_user CASCADE;\n";
    stream << "TRUNCATE TABLE user_timetable CASCADE;\n";
}
void Generator::writeTruncatesFast(ofstream& stream) const {
    stream << "TRUNCATE TABLE category,item,inventory,ingredient_list,receipt,receipt_item,location,app_user,user_timetable;";
}


void Generator::writeCategories(ofstream& stream) const {    
    for (int i = 0; i < CATEGORY_LENGTH; ++i)
        stream << "INSERT INTO category (category_id, name) VALUES (" << std::to_string(i + 1) << ", '" << to_string((ItemCategory)i) << "');\n";
}
void Generator::writeCategoriesFast(ofstream& stream) const {
    stream << "INSERT INTO category (category_id, name) VALUES\n";
    for (int i = 0; i < CATEGORY_LENGTH; ++i)
        stream << '(' << std::to_string(i + 1) << ", '" << to_string((ItemCategory)i) << (i == CATEGORY_LENGTH - 1 ? "');" : "'),"); 
}

void Generator::writeItems(ofstream& stream) const {
    for (size_t i = 0; i < allItems.size(); ++i)
        stream << *(allItems[i]) << '\n';
}
void Generator::writeItemsFast(ofstream& stream) const {
    writeFast(stream, allItems);
}

void Generator::writeInventory(ofstream& stream) const {
    for (size_t i = 0; i < allIngredients.size(); ++i)
        stream << *(allIngredients[i]) << '\n';
}
void Generator::writeInventoryFast(ofstream& stream) const {
    writeFast(stream, allIngredients);
}

void Generator::writeInventoryRelations(ofstream& stream) const {
    for (size_t i = 0; i < allRelations.size(); ++i)
        stream << *(allRelations[i]) << '\n';
}
void Generator::writeInventoryRelationsFast(ofstream& stream) const {
    writeFast(stream, allRelations);
}

void Generator::writeDays(ofstream& stream) const {
    size_t orderLen = orders.size(), currentOrderLen;
    for (size_t i = 0; i < orderLen; ++i) {
        const vector<unique_ptr<Order>>& current = orders[i];
        currentOrderLen = current.size();

        if (i != 0)
            stream << '\n';

        time_t time = current[0]->getTimestamp();
        std::tm* timepoint = std::localtime(&time);
        std::strftime(timeBuf, sizeof(timeBuf), "%d/%m/%Y", timepoint);

        stream << "--Day " << std::to_string(i + 1) << " (" << timeBuf << ") [" << std::to_string(currentOrderLen) << " orders]\n";        

        writeOneDay(stream, current);
    }
}
void Generator::writeOneDay(ofstream& stream, const vector<unique_ptr<Order>>& day) const {
    size_t dayLen = day.size();
    
    if (dayLen == 0)
        return;

    time_t time = day[0]->getTimestamp();
    std::tm* timepoint = std::localtime(&time);
    std::strftime(timeBuf, sizeof(timeBuf), "%I:%M %p", timepoint);

    stream << "\n--Order 1 (" << timeBuf << ")\n";
    for (size_t j = 0; j < dayLen; ++j) {
        stream << *(day[j]);

        if (j < dayLen - 1) {
            time = day[j + 1]->getTimestamp();
            timepoint = std::localtime(&time);
            std::strftime(timeBuf, sizeof(timeBuf), "%I:%M %p", timepoint);
        }

        if (j != dayLen - 1)
            stream << "\n--Order " << std::to_string(j + 2) << " (" << timeBuf << ')' << '\n';
    }
}
void Generator::writeDaysFast(ofstream& stream) const {
    size_t size = orders.size(), currentOrderLen;
    stream << orders[0][0]->toSql() << " VALUES ";
    for (size_t i = 0; i < size; ++i) {
        const vector<unique_ptr<Order>>& current = orders[i];
        currentOrderLen = current.size();
        bool last = i == size - 1;

        for (size_t j = 0; j < currentOrderLen; ++j) {
            stream << current[j]->toVals() << (last && j == currentOrderLen - 1 ? ';' : ',');
        }
    }

    stream << '\n' << OrderObject::getSql() << " VALUES ";
    for (size_t i = 0; i < size; ++i) {
        const vector<unique_ptr<Order>>& current = orders[i];
        currentOrderLen = current.size();
        bool last1 = i == size - 1;

        for (size_t j = 0; j < currentOrderLen; ++j) {
            const vector<unique_ptr<OrderObject>>& arr = current[j]->getItems();
            bool last2 = j == currentOrderLen - 1;
            for (size_t k = 0; k < arr.size(); ++k)
                stream << arr[k]->toVals() << (last1 && last2 && k == arr.size() - 1 ? ';' : ',');
        }
    }
}

void Generator::writeLocations(ofstream& stream) const {
    for (size_t i = 0; i < allLocations.size(); ++i)
        stream << *(allLocations[i]) << '\n';
}
void Generator::writeLocationsFast(ofstream& stream) const {
    writeFast(stream, allLocations);
}

void Generator::writeUsers(ofstream& stream) const {
    for (size_t i = 0; i < allUsers.size(); ++i)
        stream << *(allUsers[i]) << '\n';
}
void Generator::writeUsersFast(ofstream& stream) const {
    writeFast(stream, allUsers);
}

void Generator::writeTimetables(ofstream& stream) const {
    for (size_t i = 0, size = userTimetables.size(); i < size; ++i)
        stream << *userTimetables[i] << '\n';
}
void Generator::writeTimetablesFast(ofstream& stream) const {
    writeFast(stream, userTimetables);
}

void Generator::writeTeardown(const char* filename) const {
    ofstream outputFile = ofstream(filename);

    if (!outputFile.is_open()) {
        cerr << "Failed to write teardown: The output file did not open!!" << endl;
        return;
    }

    writeDestructiveGuard(outputFile);
    outputFile << "DROP TABLE IF EXISTS receipt_item;\n";
    outputFile << "DROP TABLE IF EXISTS receipt;\n";
    outputFile << "DROP TABLE IF EXISTS ingredient_list;\n";
    outputFile << "DROP TABLE IF EXISTS item;\n";
    outputFile << "DROP TABLE IF EXISTS inventory;\n";
    outputFile << "DROP TABLE IF EXISTS user_timetable;\n";
    outputFile << "DROP TABLE IF EXISTS app_user;\n";
    outputFile << "DROP TYPE IF EXISTS user_perm;\n";
    outputFile << "DROP TABLE IF EXISTS category;\n";
    outputFile << "DROP TABLE IF EXISTS location;\n";

    outputFile.close();
}

void Generator::writeReset(const char* filename) const {
    ofstream outputFile = ofstream(filename);

    if (!outputFile.is_open()) {
        cerr << "Failed to write reset: The output file did not open!!" << endl;
        return;
    }

    writeDestructiveGuard(outputFile);
    outputFile << "BEGIN;\n"
               << "LOCK TABLE category, item, inventory, ingredient_list, receipt, receipt_item, "
               << "location, app_user, user_timetable IN ACCESS EXCLUSIVE MODE;\n";
    writeTruncates(outputFile);
    if (FAST_MODE) {
        writeLocationsFast(outputFile);
        outputFile << '\n';
        writeUsersFast(outputFile);
        outputFile << '\n';
        writeCategoriesFast(outputFile);
        outputFile << '\n';
        writeItemsFast(outputFile);
        outputFile << '\n';
        writeInventoryFast(outputFile);
        outputFile << '\n';
        writeInventoryRelationsFast(outputFile);
        outputFile << '\n';
        writeDaysFast(outputFile);
        outputFile << '\n';
        writeTimetablesFast(outputFile);
    } else {
        writeLocations(outputFile);
        writeUsers(outputFile);
        writeCategories(outputFile);
        writeItems(outputFile);
        writeInventory(outputFile);
        writeInventoryRelations(outputFile);
        writeDays(outputFile);
        writeTimetables(outputFile);
    }
    writeIdentitySequenceSync(outputFile);
    writeLoaderEnd(outputFile);

    outputFile.close();
}

void Generator::writeAll(const char* filename) const {
    ofstream outputFile = ofstream(filename);

    if (!outputFile.is_open()) {
        cerr << "Failed to write all: The output file did not open!!" << endl;
        return;
    }

    writeDestructiveGuard(outputFile);
    writeLoaderStart(outputFile);
    writeTruncates(outputFile);
    outputFile << "\n-- LOCATION GENERATION\n\n";
    writeLocations(outputFile);
    outputFile << "\n-- USER GENERATION\n\n";
    writeUsers(outputFile);
    outputFile << "\n-- CATEGORY GENERATION\n\n";
    writeCategories(outputFile);
    outputFile << "\n-- ITEM GENERATION\n\n";
    writeItems(outputFile);
    outputFile << "\n-- INVENTORY GENERATION\n\n";
    writeInventory(outputFile);
    outputFile << "\n-- INVENTORY RELATIONS GENERATION\n\n";
    writeInventoryRelations(outputFile);
    outputFile << "\n-- ORDER GENERATION\n\n";
    writeDays(outputFile);
    outputFile << "\n-- TIMETABLE GENERATION\n\n";
    writeTimetables(outputFile);
    // writeIdentitySequenceSync(outputFile);
    // writeLoaderEnd(outputFile);

    outputFile.close();
}
void Generator::writeAllFast(const char* filename) const {
    ofstream outputFile = ofstream(filename);

    if (!outputFile.is_open()) {
        cerr << "Failed to write all fast: The output file did not open!!" << endl;
        return;
    }

    // writeDestructiveGuard(outputFile);
    // writeLoaderStart(outputFile);
    writeTruncatesFast(outputFile);
    writeLocationsFast(outputFile);
    writeUsersFast(outputFile);
    writeCategoriesFast(outputFile);
    writeItemsFast(outputFile);
    writeInventoryFast(outputFile);
    writeInventoryRelationsFast(outputFile);
    writeDaysFast(outputFile);
    writeTimetablesFast(outputFile);
    // writeIdentitySequenceSync(outputFile);
    // writeLoaderEnd(outputFile);

    outputFile.close();
}

// HARDCODED FUNCTIONS

void Generator::generateLocations() {
    const int len = 1;
    allLocations.resize(len);

    allLocations[0] = std::make_unique<Location>(1, "Polo Garage", "College Station", "Texas");
}

void Generator::generateUsers() {
    const int totalEmployees = (EMPLOYEES_PER_LOCATION + MANAGERS_PER_LOCATION) * allLocations.size(); 
    const char fnameFile[] = "firstname.txt";
    const char lnameFile[] = "lastname.txt";
    const char passwordFile[] = "passwords.txt";
    const int fnameLen = 38'407;
    const int lnameLen = 151'670;
    const int passwordLen = 9'998;

    ifstream firstNames = ifstream(fnameFile);
    ifstream lastNames = ifstream(lnameFile);
    ifstream passwordStream = ifstream(passwordFile);

    if (!firstNames.is_open() || !lastNames.is_open() || !passwordStream.is_open()) {
         throw std::runtime_error("Failed to open name or password files");
    }

    string passwordHeader;
    if (!std::getline(passwordStream, passwordHeader)) {
        throw std::runtime_error("Failed to read password file header");
    }

    allUsers.resize(totalEmployees);

    vector<int> fnameLines(totalEmployees);
    vector<int> lnameLines(totalEmployees);
    vector<int> passwordLines(totalEmployees);

    for (int i = 0; i < totalEmployees; ++i) {
        fnameLines[i] = (int)std::round(percentGen(rngSeed) * (fnameLen - 1));
        lnameLines[i] = (int)std::round(percentGen(rngSeed) * (lnameLen - 1));
        passwordLines[i] = (int)std::round(percentGen(rngSeed) * (passwordLen - 1));
        // cout << '(' << to_string(fnameLines[i]) << ", " << to_string(lnameLines[i]) << "), ";
    }
    cout << endl;

    std::sort(fnameLines.begin(), fnameLines.end());
    std::sort(lnameLines.begin(), lnameLines.end());
    std::sort(passwordLines.begin(), passwordLines.end());

    vector<string> names(totalEmployees);
    vector<string> passwords(totalEmployees);
    string line; 

    for (int currentLine = 0, i = 0, nextLine = fnameLines[0]; std::getline(firstNames, line) && i < totalEmployees; ++currentLine) {
        if (currentLine >= nextLine) {
            names[i] = line + ' ';
            if (++i < totalEmployees)
                nextLine = fnameLines[i];
            else break;
        }
    }
    firstNames.close();

    for (int currentLine = 0, i = 0, nextLine = lnameLines[0]; std::getline(lastNames, line) && i < totalEmployees; ++currentLine) {
        if (currentLine >= nextLine) {
            names[i] += line;
            if (++i < totalEmployees)
                nextLine = lnameLines[i];
            else break;
        }
    }
    lastNames.close();

    for (int currentLine = 0, i = 0, nextLine = passwordLines[0]; std::getline(passwordStream, line) && i < totalEmployees; ++currentLine) {
        if (currentLine >= nextLine) {
            passwords[i] = line;
            if (++i < totalEmployees)
                nextLine = passwordLines[i];
            else break;
        }
    }
    passwordStream.close();

    for (size_t i = 0, id = 1; i < allLocations.size(); ++i) {
        for (int j = 0; j < EMPLOYEES_PER_LOCATION; ++j, ++id)
            allUsers[id - 1] = std::make_unique<User>(id, names[id - 1], passwords[id - 1], UserPerm::CASHIER);
        for (int j = 0; j < MANAGERS_PER_LOCATION; ++j, ++id)
            allUsers[id - 1] = std::make_unique<User>(id, names[id - 1], passwords[id - 1], UserPerm::MANAGER);
    }

    clockOutTime = currentDay + WORKSHIFT_SECONDS + OPENING_TIME_UNIX;
}

void Generator::generateItems() {//https://www.pandaexpress.com/location/borgen-blvd-sr16/menu/a-la-carte
    const int len = 30;
    allItems.resize(len);

    allItems[0] = std::make_unique<Item>(1, ItemCategory::ENTREE, 13.80, "Cantonese BBQ Brisket");
    allItems[1] = std::make_unique<Item>(2, ItemCategory::ENTREE, 12.30, "Honey Walnut Shrimp");
    allItems[2] = std::make_unique<Item>(3, ItemCategory::ENTREE, 12.30, "Black Pepper Sirloin Steak");
    allItems[3] = std::make_unique<Item>(4, ItemCategory::ENTREE, 9.30, "Mushroom Chicken");
    allItems[4] = std::make_unique<Item>(5, ItemCategory::ENTREE, 9.30, "Kung Pao Chicken");
    allItems[5] = std::make_unique<Item>(6, ItemCategory::ENTREE, 9.30, "String Bean Chicken Breast");
    allItems[6] = std::make_unique<Item>(7, ItemCategory::ENTREE, 9.30, "The Original Orange Chicken");
    allItems[7] = std::make_unique<Item>(8, ItemCategory::ENTREE, 9.30, "SweetFire Chicken Breast");
    allItems[8] = std::make_unique<Item>(9, ItemCategory::ENTREE, 9.30, "Honey Sesame Chicken Breast");
    allItems[9] = std::make_unique<Item>(10, ItemCategory::ENTREE, 9.30, "Grilled Teriyaki Chicken");
    allItems[10] = std::make_unique<Item>(11, ItemCategory::ENTREE, 9.30, "Broccoli Beef");
    allItems[11] = std::make_unique<Item>(12, ItemCategory::ENTREE, 9.30, "Beijing Beef");
    allItems[12] = std::make_unique<Item>(13, ItemCategory::SIDE, 4.65, "White Steamed Rice");
    allItems[13] = std::make_unique<Item>(14, ItemCategory::SIDE, 4.65, "Fried Rice");
    allItems[14] = std::make_unique<Item>(15, ItemCategory::SIDE, 4.65, "Chow Mein");
    allItems[15] = std::make_unique<Item>(16, ItemCategory::SIDE, 4.65, "Super Greens");
    allItems[16] = std::make_unique<Item>(17, ItemCategory::SIDE, 2.10, "Veggie Spring Roll");
    allItems[17] = std::make_unique<Item>(18, ItemCategory::SIDE, 2.10, "Chicken Egg Roll");
    allItems[18] = std::make_unique<Item>(19, ItemCategory::SIDE, 2.10, "Cream Cheese Rangoon");
    allItems[19] = std::make_unique<Item>(20, ItemCategory::DRINK, 3.60, "Watermelon Mango Flavored Refresher");
    allItems[20] = std::make_unique<Item>(21, ItemCategory::DRINK, 2.70, "Fanta Orange");
    allItems[21] = std::make_unique<Item>(22, ItemCategory::DRINK, 2.70, "Dr Pepper");
    allItems[22] = std::make_unique<Item>(23, ItemCategory::DRINK, 2.70, "Dasani");
    allItems[23] = std::make_unique<Item>(24, ItemCategory::DRINK, 3.50, "Smartwater");
    allItems[24] = std::make_unique<Item>(25, ItemCategory::DRINK, 2.70, "Sprite");
    allItems[25] = std::make_unique<Item>(26, ItemCategory::DRINK, 2.70, "Minute Maid Lemonade");
    allItems[26] = std::make_unique<Item>(27, ItemCategory::DRINK, 3.60, "Pomegranate Pineapple Flavored Lemonade");
    allItems[27] = std::make_unique<Item>(28, ItemCategory::DRINK, 2.70, "Coca Cola Zero Sugar");
    allItems[28] = std::unique_ptr<Item>(new Item(29,ItemCategory::ENTREE, 10.00 , "Cantonese BBQ Brisket", {6, 2}, {8, 10}));
    allItems[29] = std::unique_ptr<Item>(new Item(30,ItemCategory::ENTREE, 10.00 , "Hot Orange Chicken", {7, 11}, {9, 30}));
    //allItems[2] = new Item(2, , ""));

    for (int i = 0; i < CATEGORY_LENGTH; ++i) {
        itemByCategory.push_back({}); // gotta init your vector :)
        unavailableItemsByCategory[i] = 0;
    }

    for (int i = 0; i < len; ++i) {
        itemByCategory[allItems[i].get()->getCategory()].push_back(allItems[i]);
    }
        
    unavailableItems = {};
    unavailableItems.reserve(len);
}

void Generator::generateIngredients(time_t startTime) {
    const int len = 28;
    allIngredients.resize(len);

    allIngredients[0] = std::make_unique<Ingredient>(1, "Chicken", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[1] = std::make_unique<Ingredient>(2, "BBQ Sauce", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[2] = std::make_unique<Ingredient>(3, "Beef", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[3] = std::make_unique<Ingredient>(4, "Shrimp", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[4] = std::make_unique<Ingredient>(5, "Honey Sauce", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[5] = std::make_unique<Ingredient>(6, "Walnuts", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[6] = std::make_unique<Ingredient>(7, "Black Pepper", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[7] = std::make_unique<Ingredient>(8, "Vegetables", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[8] = std::make_unique<Ingredient>(9, "Mushrooms", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[9] = std::make_unique<Ingredient>(10, "Spices", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[10] = std::make_unique<Ingredient>(11, "Assorted Nuts", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[11] = std::make_unique<Ingredient>(12, "Peppers", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[12] = std::make_unique<Ingredient>(13, "Soy Sauce", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[13] = std::make_unique<Ingredient>(14, "Orange Sauce", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[14] = std::make_unique<Ingredient>(15, "White Rice", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[15] = std::make_unique<Ingredient>(16, "Noodles", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[16] = std::make_unique<Ingredient>(17, "Cream Cheese", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[17] = std::make_unique<Ingredient>(18, "Watermelon", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[18] = std::make_unique<Ingredient>(19, "Mango", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[19] = std::make_unique<Ingredient>(20, "Pomegranate", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[20] = std::make_unique<Ingredient>(21, "Pineapple", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[21] = std::make_unique<Ingredient>(22, "Fanta Orange", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[22] = std::make_unique<Ingredient>(23, "Dr Pepper", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[23] = std::make_unique<Ingredient>(24, "Dasani", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[24] = std::make_unique<Ingredient>(25, "Smartwater", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[25] = std::make_unique<Ingredient>(26, "Sprite", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[26] = std::make_unique<Ingredient>(27, "Minute Maid Lemonade", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    allIngredients[27] = std::make_unique<Ingredient>(28, "Coca Cola Zero Sugar", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);
    //allIngredients[2] = new Ingredient(2, "", 50, 25, startTime + 86'400 * 7, startTime + 86'400 * 14);

}

void Generator::generateIngredientRelations() {
    const int len = 67;
    allRelations.resize(len);

    allRelations[0] = std::make_unique<IngredientRelation>(1, 2);
    allRelations[1] = std::make_unique<IngredientRelation>(1, 3);
    allRelations[2] = std::make_unique<IngredientRelation>(2, 4);
    allRelations[3] = std::make_unique<IngredientRelation>(2, 5);
    allRelations[4] = std::make_unique<IngredientRelation>(2, 6);
    allRelations[5] = std::make_unique<IngredientRelation>(3, 7);
    allRelations[6] = std::make_unique<IngredientRelation>(3, 8);
    allRelations[7] = std::make_unique<IngredientRelation>(3, 3);
    allRelations[8] = std::make_unique<IngredientRelation>(4, 1);
    allRelations[9] = std::make_unique<IngredientRelation>(4, 9);
    allRelations[10] = std::make_unique<IngredientRelation>(4, 8);
    allRelations[11] = std::make_unique<IngredientRelation>(5, 8);
    allRelations[12] = std::make_unique<IngredientRelation>(5, 10);
    allRelations[13] = std::make_unique<IngredientRelation>(5, 11);
    allRelations[14] = std::make_unique<IngredientRelation>(5, 12);
    allRelations[15] = std::make_unique<IngredientRelation>(6, 1);
    allRelations[16] = std::make_unique<IngredientRelation>(6, 8);
    allRelations[17] = std::make_unique<IngredientRelation>(6, 13);
    allRelations[18] = std::make_unique<IngredientRelation>(7, 1);
    allRelations[19] = std::make_unique<IngredientRelation>(7, 14);
    allRelations[20] = std::make_unique<IngredientRelation>(8, 1);
    allRelations[21] = std::make_unique<IngredientRelation>(8, 8);
    allRelations[22] = std::make_unique<IngredientRelation>(8, 12);
    allRelations[23] = std::make_unique<IngredientRelation>(9, 1);
    allRelations[24] = std::make_unique<IngredientRelation>(9, 8);
    allRelations[25] = std::make_unique<IngredientRelation>(9, 10);
    allRelations[26] = std::make_unique<IngredientRelation>(10, 1);
    allRelations[27] = std::make_unique<IngredientRelation>(10, 10);
    allRelations[28] = std::make_unique<IngredientRelation>(11, 3);
    allRelations[29] = std::make_unique<IngredientRelation>(11, 8);
    allRelations[30] = std::make_unique<IngredientRelation>(11, 13);
    allRelations[31] = std::make_unique<IngredientRelation>(11, 10);
    allRelations[32] = std::make_unique<IngredientRelation>(12, 3);
    allRelations[33] = std::make_unique<IngredientRelation>(12, 13);
    allRelations[34] = std::make_unique<IngredientRelation>(12, 10);
    allRelations[35] = std::make_unique<IngredientRelation>(12, 12);
    allRelations[36] = std::make_unique<IngredientRelation>(13, 15);
    allRelations[37] = std::make_unique<IngredientRelation>(14, 15);
    allRelations[38] = std::make_unique<IngredientRelation>(14, 8);
    allRelations[39] = std::make_unique<IngredientRelation>(14, 13);
    allRelations[40] = std::make_unique<IngredientRelation>(15, 16);
    allRelations[41] = std::make_unique<IngredientRelation>(15, 13);
    allRelations[42] = std::make_unique<IngredientRelation>(15, 8);
    allRelations[43] = std::make_unique<IngredientRelation>(15, 10);
    allRelations[44] = std::make_unique<IngredientRelation>(16, 8);
    allRelations[45] = std::make_unique<IngredientRelation>(16, 10);
    allRelations[46] = std::make_unique<IngredientRelation>(17, 8);
    allRelations[47] = std::make_unique<IngredientRelation>(17, 10);
    allRelations[48] = std::make_unique<IngredientRelation>(17, 12);
    allRelations[49] = std::make_unique<IngredientRelation>(18, 1);
    allRelations[50] = std::make_unique<IngredientRelation>(18, 8);
    allRelations[51] = std::make_unique<IngredientRelation>(18, 10);
    allRelations[52] = std::make_unique<IngredientRelation>(18, 13);
    allRelations[53] = std::make_unique<IngredientRelation>(19, 17);
    allRelations[54] = std::make_unique<IngredientRelation>(19, 10);
    allRelations[55] = std::make_unique<IngredientRelation>(19, 8);
    allRelations[56] = std::make_unique<IngredientRelation>(20, 18);
    allRelations[57] = std::make_unique<IngredientRelation>(20, 19);
    allRelations[58] = std::make_unique<IngredientRelation>(21,22);
    allRelations[59] = std::make_unique<IngredientRelation>(22,23);
    allRelations[60] = std::make_unique<IngredientRelation>(23,24);
    allRelations[61] = std::make_unique<IngredientRelation>(24,25);
    allRelations[62] = std::make_unique<IngredientRelation>(25, 26);
    allRelations[63] = std::make_unique<IngredientRelation>(26, 27);
    allRelations[64] = std::make_unique<IngredientRelation>(27, 20);
    allRelations[65] = std::make_unique<IngredientRelation>(27, 21);
    allRelations[66] = std::make_unique<IngredientRelation>(28, 28);
    //allRelations[6] = new IngredientRelation();
}
