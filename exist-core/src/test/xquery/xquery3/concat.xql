(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.0";

module namespace concat="http://exist-db.org/xquery/test/string-concatenation";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $concat:XML :=
    <a>
        <b>
            <c>CC1</c>
            <c>CC2</c>
        </b>
    </a>;

declare
    %test:assertEquals("ungrateful")
function concat:strings() {
    ('un' ||  'grateful')
};

declare
    %test:assertEquals("The result is 22.5")
function concat:numbers() {
    ("The result is " ||  22.5)
};

declare
    %test:assertEquals("unGRATEFUL")
function concat:function-call() {
    ("un" ||  upper-case("grateful"))
};

declare
    %test:assertEquals("The result is ")
function concat:empty-sequence1() {
    ("The result is " ||  ())
};

declare
    %test:assertTrue
function concat:empty-sequence2() {
    (() ||  ()) instance of xs:string
};

declare
    %test:assertTrue
function concat:empty-sequence3() {
    (() ||  ()) eq ""
};

declare
    %test:assertEquals(1)
function concat:empty-strings() {
    count("" ||  "")
};

declare
    %test:assertEquals("zzzzz123")
function concat:nested() {
    (("zzz" || "zz") || "123")
};

declare
    %test:assertEquals("ungrateful death")
function concat:multiple() {
    ('un' ||  () || 'grateful' || ' ' || "death")
};

declare
    %test:assertEquals("12-16")
function concat:precedence1() {
    12 || 34 - 50
};

declare
    %test:assertTrue
function concat:precedence2() {
    "1234" eq 12 || 34
};

declare
    %test:assertTrue
function concat:nodes() {
    $concat:XML/b/c[1] || 2 eq "CC12"
};

declare
    %test:assertError("FOTY0013")
function concat:bad-argument() {
    ("abc" || "abc" ||  fn:concat#3)
};

(: declare
    %test:assertEquals("<a b='ccdd'/>") 
function concat:attributes1() {
    <a b="{ 'cc' || 'dd'}"/>
}; :)
