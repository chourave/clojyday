# Ferje

> **Jour férié**  
> IPA /ʒuʁ fe.ʁje/  
> French : bank holiday, public holiday

Ferje gives you an idiotmatic Clojure interface to compute holiday dates without
needing network access or a database. The awesome [Jollyday](http://jollyday.sourceforge.net)
library does the heavy lifting behind the scenes.


- [Sample use](#usage)
- [API](doc/api.md)
- [Configuration](doc/configuration.md)

## <a name="usage"></a>Usage<sup id="a1">[1](#f1)</sup>

> I wonder what holidays they have in the US.

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

> Still no luck! Let’s search a wider time span, from February to April.

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
> There must be some extra holidays that apply only to Cambridge, not the whole of Massachusetts.
> Let’s find out.

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

> So, the holiday my friend Rahul celebrates in March is 
> [Evacuation Day](https://en.wikipedia.org/wiki/Evacuation_Day_(Massachusetts)).
> Who knew!

## Footnotes

<b id="f1">1</b> The REPL is [slightly customized](doc/repl-setup-for-samples.md)
to make the examples easier on the eyes. [↩](#a1)
