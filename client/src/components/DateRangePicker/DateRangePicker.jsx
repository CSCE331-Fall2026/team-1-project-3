//file is for creating the date pickers for manager graphs; adapted from react-aria-components library

import {
  DateRangePicker as AriaDateRangePicker, DateInput, DateSegment, Group, Label, Button, Popover, RangeCalendar, CalendarGrid, CalendarCell, Heading
} from "react-aria-components";
import "./DateRangePicker.css";

export function DateRangePicker({ label, ...props }) {
  return (
    <AriaDateRangePicker {...props}>
      <Label>{label}</Label>
      <Group className="dateRangeGroup">
        <div className="dateFields">
          <DateInput slot="start">
            {(segment) => (
              <DateSegment segment={segment} />
            )}
          </DateInput>

          <span aria-hidden="true">-</span>

          <DateInput slot="end">
            {(segment) => (
              <DateSegment segment={segment} />
            )}
          </DateInput>
          
        </div>

        <Button className="calendarButton">📅</Button>
      </Group>

      <Popover className="dateRangePopover">
        <RangeCalendar>
          <header className="calendarHeader">
            <Button slot="previous">◀</Button>
            <Heading />
            <Button slot="next">▶</Button>
          </header>

          <CalendarGrid>
            {(date) => (
              <CalendarCell date={date} />
            )}
          </CalendarGrid>
        </RangeCalendar>
      </Popover>
    </AriaDateRangePicker>
  );
}
