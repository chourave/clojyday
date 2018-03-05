# Places

A place is specified as a vector of keywords. Each added keyword refines the location.

- `[:us]` will query for nationwide holiday in the United States of America.
- `[:us :ny]` will query for holidays in the state of New York (including nationwide holidays).
- `[:us :ny :nyc]` query for holidays in New-York City (including the previous two categories).

Note that it isn’t possible to query only for holidays specific to, e.g., New York City.

When a place specification is of one single element, the vector around that element can be
omitted, like so: `:us`.


# Time intervals

Queries for holidays are always made over a time interval. You can specify

- a whole year: `2017` will query for all holidays of 2017
- a month: `{2017 5}` will query for all holidays of May 2017
- an explicit interval: `[(java-time/local-date 2017 5 1) (java-time/local-date 2017 6 15)]`
  will query for all holidays between the first of May and the fifteenth of June
  2017, both inclusive (where `java-time/local-date` is a function returning a java 8 date)
- it is also possible to use the short syntax for months or years in intervals,
  for instance `[2017 2018]` will query for all holidays of 2017 and 2018
