(ns com.flashlightdb.ceilingbounce.csv
  (:import (java.io FileReader FileWriter BufferedReader File)))

(defn quote-if-string [x]
  (if (and (string? x) (not (empty? x)))
    (str \" x \")
    x))

(defn seq->csv [s]
  (apply str (concat (interpose \, (map quote-if-string s))
                     [\return \newline])))

(defn write-csv-line [output-value filename]
  (spit filename (seq->csv output-value) :append true))

(defn csv->seq [f]
  (with-open [reader (BufferedReader. (FileReader. f))]
    (doall (line-seq reader))))
