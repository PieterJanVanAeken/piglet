A = LOAD '$inbase/input/unsorted_top.csv' USING PigStorage(',');
B = order A by $1 asc, $2 desc;
C = limit B 4;
STORE C INTO '$outfile';
