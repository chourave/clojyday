# How I formatted the REPL samples

I wanted to pepper the documentation with lots of little REPL sessions that
should look nice and be easy to read. To get there, had to I tweak my environment
a little bit.

First, I ran all the result through `pprint`, with a right margin of 110
characters.

The next step was to find a more pleasant way to format Java `Date` objects.
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

Finally, I like my forms to round-trip safely back from text to data. This
has no incidence on how the repl samples look, I’m listing it here for
completeness’ sake.

To be able to read back #date forms, I added an entry to `*data-readers*`. 


``` clojure
(defn read-date [d] (time/local-date "yyyy-MM-dd" d))
(set! *data-readers* (assoc *data-readers* 'date #'read-date))
```

I used `set!` to set the new reader from the REPL. Outside of a REPL context,
I would have used `alter-var-root` or a `data_readers.clj` file instead.

Note that defining a reader for `#date` is not strictly kosher, as Clojure
reserves tags without a namespace qualifier for its own use.
