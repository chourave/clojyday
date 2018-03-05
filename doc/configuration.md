# Configuration

Ferje comes with 65 predefined calendars from Jollyday.
And for your more refined holiday computation needs, you can make your own
holiday configuration


## Configuration formats

Ferje currently supports two (and a half) configuration formats.


### EDN

```clojure
{hierarchy   us
 description "United States"

 ;; These are the nationwide common holidays

 holidays [(january 1)
           (july 4)
           (november 11)
           (december 25)
           (last monday of may)
           (first monday of september)
           (fourth thursday of november)]

 ;; These are the additional NY holidays

 sub-configurations
 [{hierarchy   ny
   description "New York"

   holidays [(february 12)
             (tuesday after first monday of november)
             (third monday of january)
             (third monday of february)
             (second monday of october)
             easter-monday]

   ;; These are the additional NYC holidays

   sub-configurations
   [{hierarchy    nyc
     description "New York City"

     holidays [(first thursday of june)]}]}]}
```

### XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<tns:Configuration hierarchy="us" description="United States" xmlns:tns="http://www.example.org/Holiday" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.example.org/Holiday Holiday.xsd ">

  <!-- These are the nationwide common holidays. -->

  <tns:Holidays>
          <tns:Fixed month="JANUARY" day="1" />
          <tns:Fixed month="JULY" day="4" />
          <tns:Fixed month="NOVEMBER" day="11" />
          <tns:Fixed month="DECEMBER" day="25" />
          <tns:FixedWeekday which="LAST" weekday="MONDAY" month="MAY" />
          <tns:FixedWeekday which="FIRST" weekday="MONDAY" month="SEPTEMBER" />
          <tns:FixedWeekday which="FOURTH" weekday="THURSDAY" month="NOVEMBER"/>
  </tns:Holidays>

  <!-- These are the additional NY holidays -->

  <tns:SubConfigurations hierarchy="ny" description="New York">
        <tns:Holidays>
                <tns:Fixed month="FEBRUARY" day="12"/>
                <tns:RelativeToWeekdayInMonth weekday="TUESDAY" when="AFTER">
                        <tns:FixedWeekday which="FIRST" weekday="MONDAY" month="NOVEMBER"/>
                </tns:RelativeToWeekdayInMonth>
                <tns:FixedWeekday which="THIRD" weekday="MONDAY" month="JANUARY" />
                <tns:FixedWeekday which="THIRD" weekday="MONDAY" month="FEBRUARY" />
                <tns:FixedWeekday which="SECOND" weekday="MONDAY" month="OCTOBER" />
                <tns:ChristianHoliday type="EASTER_MONDAY"/>
        </tns:Holidays>

        <!-- These are the additional NYC holidays. -->

        <tns:SubConfigurations hierarchy="nyc" description="New York City">
                <tns:Holidays>
                        <tns:FixedWeekday which="FIRST" weekday="THURSDAY" month="JUNE"/>
                </tns:Holidays>
        </tns:SubConfigurations>
  </tns:SubConfigurations>
</tns:Configuration>
```

Ferje can also read Jollyday’s
[XML configuration](http://jollyday.sourceforge.net/properties.html#XML_Configuration)
files, using either Jollyday’s JAXB-based parser, or a native Clojure parser
(that’s the half extra configuration format).

The Clojure parser can read the same configuration files the Jollyday parser
can read, and it has a few other characteristics that make it my preferred XML configuration
source:

- it is faster
- it doesn’t rely on JAXB, making it easier to use in Java 9 environments
- it is a bit more flexible about the XML it will accept
    - it doesn’t impose any restrictions on the ordering of XML tags
    - it ignores XML namespaces

## Using a custom configuration
