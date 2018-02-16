# Ferje

Computes holiday dates without need for network access or a database.

> **Jour férié**  
> IPA /ʒuʁ fe.ʁje/  
> French : bank holiday, public holiday

Ferje harnesses the power of the awesome [Jollyday](http://jollyday.sourceforge.net) library,
expressing it as idiomatic Clojure.

## Usage

> I wonder what holidays they have over in the US.

``` clojure
> (require '[ferje.core :as ferje])

> (ferje/holidays :us 2018)

#{{:date #date "2018-07-04", :description nil, :description-key "INDEPENDENCE", :official? true}
  {:date #date "2018-11-11", :description nil, :description-key "VETERANS", :official? true}
  {:date #date "2018-05-28", :description nil, :description-key "MEMORIAL", :official? true}
  {:date #date "2018-12-25", :description nil, :description-key "CHRISTMAS", :official? true}
  {:date #date "2018-11-22", :description nil, :description-key "THANKSGIVING", :official? true}
  {:date #date "2018-09-03", :description nil, :description-key "LABOUR_DAY", :official? true}
  {:date #date "2018-01-01", :description nil, :description-key "NEW_YEAR", :official? true}}
```

> That’s weird. My friend Rahul lives over there, and I’m sure he told me about a holiday in march,
> but I can’t find one. Let’s just focus on March, perhaps I’m overlooking something.

``` clojure
> (ferje/holidays :us {2018 3})

#{}
```

> Nope, it’s not just me. Merhaps it’s a local holiday. Rahul lives in Massachusetts, let’s try that.

``` clojure
> (ferje/holidays [:us :ma] {2018 3})

#{}
```

> No luck! Let’s search a wider time span, from February to April.

``` clojure
> (ferje/holidays [:us :ma]  [{2018 2} {2018 4}])

#{{:date #date "2018-02-19", :description nil, :description-key "WASHINGTONS_BIRTHDAY", :official? true}
  {:date #date "2018-04-16", :description nil, :description-key "PATRIOT", :official? true}}
```

> That’s better, but I’m stil pretty sure Rahul told me that they had a holiday in March.
> Perhaps it depends on *where* precisely you live in Massachusetts?

``` clojure
> (ferje/calendar-hierarchy [:us :ma])

{:id :ma,
 :description "Massachusetts",
 :description-key "us.ma",
 :zones
 {:sc {:id :sc, :description "Suffolk County", :description-key "us.ma.sc", :zones nil},
  :ca {:id :ca, :description "Cambridge", :description-key "us.ma.ca", :zones nil}}}
```

> A-ha, they have a zone called `:ca` for Cambridge. As it happens, that’s where Rahul lives.
> Looks like there are some extra holidays that apply only to Cambridge, not the whole of Massachusetts.

```clojure
> (ferje/holidays [:us :ma :ca]  [{2018 2} {2018 4}])

#{{:date #date "2018-03-17", :description nil, :description-key "EVACUATION", :official? true}
  {:date #date "2018-02-19", :description nil, :description-key "WASHINGTONS_BIRTHDAY", :official? true}
  {:date #date "2018-04-16", :description nil, :description-key "PATRIOT", :official? true}}
```

> Yup, that looks right. I wonder how those holidays are called.

``` clojure
> (->> (ferje/holidays [:us :ma :ca]  [{2018 2} {2018 4}]) 
       (map ferje/localize) 
	   (map #(select-keys % [:date :description])))

({:date #date "2018-03-17", :description "Evacuation Day"}
 {:date #date "2018-02-19", :description "Washington's Birthday"}
 {:date #date "2018-04-16", :description "Patriots' Day"})
```

## How I formatted the REPL samples

I wanted the usage cases I showed in this documentation to be realistic, look nice
and be easy to read. To get there, I tweaked my environment a little bit.

First, I ran all the result through `pprint`, with a right margin of 110
characters.

The next step was formatting Java `Date` objects.
By default, Java 8 dates are displayed like this in the REPL:

```clojure
#object[java.time.LocalDate 0x1d29703d "2017-08-01"]
```

Workable, but clunky. I prefer them to look like this: `#date "2017-08-01"`.

The trick is to define the `print-method` for `java.time.LocalDate` objects.

``` clojure
(defmethod print-method java.time.LocalDate [d w]
  (doto w 
    (.write "#date \"")
	(.write (time/format "yyyy-MM-dd" d))
	(.write "\"")))
```

Finally, while this is not needed for printing dates to the REPL, I like my
forms to round-trip safely back from text to data.
To this end, I added an entry to `*data-readers*`. 


Since I’m in the REPL, I used `set!` to set the new reader. Otherwise, I would
have used `alter-var-root` or the `data_readers.clj` file.

``` clojure
(defn read-date [d] (time/local-date "yyyy-MM-dd" d))
(set! *data-readers* (assoc *data-readers* 'date #'read-date))
```

Note that this is not strictly kosher, as Clojure
reserves tags without a namespace qualifier for its own use.
