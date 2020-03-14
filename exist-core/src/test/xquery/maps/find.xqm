xquery version "3.1";

module namespace mf="http://exist-db.org/xquery/test/maps/find";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $mf:responses := [
        map{0:'no', 1:'yes'},
        map{0:'non', 1:'oui'},
        map{0:'nein', 1:('ja', 'doch')}
];

declare variable $mf:inventory := map {
        "name":"car",
        "id":"QZ123",
        "parts": [
                map {
                    "name":"engine",
                    "id":"YW678",
                    "parts":[]
                }
        ]
};


declare
    %test:assertEquals("no", "non", "nein")
function mf:array-1() {
    map:find($mf:responses, 0)?*
};

declare
    %test:assertEquals("yes", "oui", "ja", "doch")
function mf:array-2() {
    map:find($mf:responses, 1)?*
};

declare
    %test:assertTrue
function mf:array-empty() {
    fn:empty(map:find($mf:responses, 2)?*)
};

declare
    %test:assertXPath("fn:deep-equal([[map{'name':'engine', 'id':'YW678', 'parts':[]}], []], $result)")
function mf:recursive() {
    map:find($mf:inventory, "parts")
};