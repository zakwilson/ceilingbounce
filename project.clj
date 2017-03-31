(defproject com.flashlightdb/ceilingbounce "0.2.0-SNAPSHOT"
  :description "Ceilingbounce - an app for flashlight testing"
  :url "http://github.com/zakwilson/ceilingbounce"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :global-vars {*warn-on-reflection* true}

  :source-paths ["src/clojure" "src"]
  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :plugins [[lein-droid "0.4.6"]]
  :repositories [["jitpack" "https://jitpack.io"]]
  :dependencies [[org.clojure-android/clojure "1.7.0-r2"]
                 [neko/neko "4.0.0-alpha5"]
                 [org.clojure/data.csv "0.1.3"]
                 [org.clojure/core.async "0.2.371"]
                 [com.github.PhilJay/MPAndroidChart "v2.2.3"]
                 [amalloy/ring-buffer "1.2.1"]
                 [overtone/at-at "1.2.0"]]
  :profiles {:default [:dev]

             :dev
             [:android-common :android-user
              {:dependencies [[org.clojure/tools.nrepl "0.2.10"]
                              ;[org.clojure/clojurescript "1.7.170"]
                              ;[org.clojure/tools.reader "0.10.0"]
                              ]
               :target-path "target/debug"
               :android {:aot [neko.activity
                               neko.debug
                               neko.notify
                               neko.resource
                               neko.find-view
                               neko.threading
                               neko.log
                               neko.ui
                               clojure.data.csv
                               clojure.java.io
                               clojure.core.async
                               clojure.tools.nrepl.server
                               #"cider\..+"
                               com.flashlightdb.ceilingbounce.main]
                         :rename-manifest-package "com.flashlightdb.ceilingbounce.debug"
                         :manifest-options {:app-name "ceilingbounce (debug)"}}}]
             :lean
             [:dev
              {:dependencies ^:replace [[org.skummet/clojure "1.7.0-r2"]]
               :exclusions [[org.clojure/clojure]
                            [org.clojure-android/clojure]]
               :jvm-opts ["-Dclojure.compile.ignore-lean-classes=true"]
               :global-vars ^:replace {clojure.core/*warn-on-reflection* true}
               :android {:lean-compile true
                         :proguard-execute true
                         :proguard-conf-path "build/proguard-minify.cfg"

                         :rename-manifest-package "com.flashlightdb.ceilingbounce.lean"
                         :manifest-options {:app-name "ceilingbounce (lean)"}}}]
             :release
             [:android-common
              {:target-path "target/release"
               :android
               {;; :keystore-path "/home/user/.android/private.keystore"
                ;; :key-alias "mykeyalias"
                ;; :sigalg "MD5withRSA"

                :ignore-log-priority [:debug :verbose]
                :aot :all
                :build-type :release}}]}

  :android {;; Specify the path to the Android SDK directory.
;            :sdk-path "/home/zak/code/android-sdk-linux"

            ;; Try increasing this value if dexer fails with
            ;; OutOfMemoryException. Set the value according to your
            ;; available RAM.

            ; core-library here for tikkba, but this is probably a bad idea
            
            :dex-opts ["-JXmx4096M"]
            :multi-dex true
            :multi-dex-proguard-conf-path "build/proguard-multi-dex.cfg"
            :target-version "22"
            :aot-exclude-ns ["clojure.parallel" "clojure.core.reducers"
                             "cider.nrepl" "cider-nrepl.plugin"
                             "cider.nrepl.middleware.util.java.parser"
                             #"cljs-tooling\..+"]})
