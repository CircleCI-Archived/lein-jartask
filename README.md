# jartask

A Leiningen plugin to run arbitrary lein tasks contained in other jars.

## Usage

FIXME: Use this for user-level plugins:

Put `[jartask "0.1.0-SNAPSHOT"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
jartask 0.1.0-SNAPSHOT`.

    $ lein jartask [foo/bar "1.2.3"] run :baz

This will download [foo/bar "1.2.3"] if necessary, and run the `lein run :baz` task, defined in bar's project.clj

## License

Copyright © 2014 CircleCI

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
