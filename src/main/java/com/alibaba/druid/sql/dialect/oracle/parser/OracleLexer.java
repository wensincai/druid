/*
 * Copyright 1999-2011 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.druid.sql.dialect.oracle.parser;

import static com.alibaba.druid.sql.parser.CharTypes.isIdentifierChar;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.druid.sql.parser.Keywords;
import com.alibaba.druid.sql.parser.Lexer;
import com.alibaba.druid.sql.parser.NotAllowCommentException;
import com.alibaba.druid.sql.parser.SQLParseException;
import com.alibaba.druid.sql.parser.Token;

import static com.alibaba.druid.sql.parser.LayoutCharacters.EOI;

public class OracleLexer extends Lexer {

    public final static Keywords DEFAULT_ORACLE_KEYWORDS;

    static {
        Map<String, Token> map = new HashMap<String, Token>();
        
        map.putAll(Keywords.DEFAULT_KEYWORDS.getKeywords());

        map.put("BEGIN", Token.BEGIN);
        map.put("COLUMN", Token.COLUMN);
        map.put("COMMENT", Token.COMMENT);
        map.put("COMMIT", Token.COMMIT);
        map.put("CONNECT", Token.CONNECT);

        map.put("CROSS", Token.CROSS);
        map.put("CURSOR", Token.CURSOR);
        map.put("DECLARE", Token.DECLARE);
        map.put("ERRORS", Token.ERRORS);
        map.put("EXCEPTION", Token.EXCEPTION);

        map.put("EXCLUSIVE", Token.EXCLUSIVE);
        map.put("EXTRACT", Token.EXTRACT);
        map.put("GOTO", Token.GOTO);
        map.put("GRANT", Token.GRANT);
        map.put("IF", Token.IF);

        map.put("LIMIT", Token.LIMIT);
        map.put("LOCAL", Token.LOCAL);
        map.put("LOOP", Token.LOOP);
        map.put("MATCHED", Token.MATCHED);
        map.put("MERGE", Token.MERGE);

        map.put("MODE", Token.MODE);
        map.put("MODEL", Token.MODEL);
        map.put("NOWAIT", Token.NOWAIT);
        map.put("OF", Token.OF);
        map.put("PRIOR", Token.PRIOR);

        map.put("PROCEDURE", Token.PROCEDURE);
        map.put("REJECT", Token.REJECT);
        map.put("RETURNING", Token.RETURNING);
        map.put("SAVEPOINT", Token.SAVEPOINT);
        map.put("SESSION", Token.SESSION);

        map.put("SHARE", Token.SHARE);
        map.put("START", Token.START);
        map.put("SYSDATE", Token.SYSDATE);
        map.put("UNLIMITED", Token.UNLIMITED);
        map.put("USING", Token.USING);

        map.put("WAIT", Token.WAIT);
        map.put("WITH", Token.WITH);

        DEFAULT_ORACLE_KEYWORDS = new Keywords(map);
    }

    public OracleLexer(char[] input, int inputLength, boolean skipComment){
        super(input, inputLength, skipComment);
        super.keywods = DEFAULT_ORACLE_KEYWORDS;
    }

    public OracleLexer(String input){
        super(input);
        super.keywods = DEFAULT_ORACLE_KEYWORDS;
    }

    public void scanVariable() {
        if (ch == '@') {
            scanChar();
            token = Token.MONKEYS_AT;
            return;
        }

        if (ch != ':' && ch != '#' && ch != '$') {
            throw new SQLParseException("illegal variable");
        }

        int hash = ch;

        np = bp;
        sp = 1;
        char ch;

        boolean quoteFlag = false;
        boolean mybatisFlag = false;
        if (charAt(bp + 1) == '"') {
            hash = 31 * hash + '"';
            bp++;
            sp++;
            quoteFlag = true;
        } else if (charAt(bp + 1) == '{') {
            hash = 31 * hash + '"';
            bp++;
            sp++;
            mybatisFlag = true;
        }
        
        for (;;) {
            ch = charAt(++bp);

            if (!isIdentifierChar(ch)) {
                break;
            }

            hash = 31 * hash + ch;

            sp++;
            continue;
        }
        
        if (quoteFlag) {
            if (ch != '"') {
                throw new SQLParseException("syntax error");
            }
            hash = 31 * hash + '"';
            ++bp;
            sp++;
        } else if (mybatisFlag) {
            if (ch != '}') {
                throw new SQLParseException("syntax error");
            }
            hash = 31 * hash + '"';
            ++bp;
            sp++;
        }

        this.ch = charAt(bp);

        stringVal = addSymbol(hash);
        Token tok = keywods.getKeyword(stringVal);
        if (tok != null) {
            token = tok;
        } else {
            token = Token.VARIANT;
        }
    }

    public void scanComment() {
        if (ch != '/' && ch != '-') {
            throw new IllegalStateException();
        }

        np = bp;
        sp = 0;
        scanChar();

        // /*+ */
        if (ch == '*') {
            scanChar();
            sp++;

            while (ch == ' ') {
                scanChar();
                sp++;
            }

            boolean isHint = false;
            int startHintSp = sp + 1;
            if (ch == '+') {
                isHint = true;
                scanChar();
                sp++;
            }

            for (;;) {
                if (ch == '*' && charAt(bp + 1) == '/') {
                    sp += 2;
                    scanChar();
                    scanChar();
                    break;
                }

                scanChar();
                sp++;
            }

            if (isHint) {
                stringVal = subString(np + startHintSp, (sp - startHintSp) - 1);
                token = Token.HINT;
            } else {
                stringVal = subString(np, sp);
                token = Token.MULTI_LINE_COMMENT;
            }

            if (token != Token.HINT && !isAllowComment()) {
                throw new NotAllowCommentException();
            }

            return;
        }

        if (!isAllowComment()) {
            throw new NotAllowCommentException();
        }

        if (ch == '/' || ch == '-') {
            scanChar();
            sp++;

            for (;;) {
                if (ch == '\r') {
                    if (charAt(bp + 1) == '\n') {
                        sp += 2;
                        scanChar();
                        break;
                    }
                    sp++;
                    break;
                } else if (ch == EOI) {
                    break;
                }

                if (ch == '\n') {
                    scanChar();
                    sp++;
                    break;
                }

                scanChar();
                sp++;
            }

            stringVal = subString(np + 1, sp);
            token = Token.LINE_COMMENT;
            return;
        }
    }

    public void scanNumber() {
        np = bp;

        if (ch == '-') {
            sp++;
            ch = charAt(++bp);
        }

        for (;;) {
            if (ch >= '0' && ch <= '9') {
                sp++;
            } else {
                break;
            }
            ch = charAt(++bp);
        }

        boolean isDouble = false;

        if (ch == '.') {
            if (charAt(bp + 1) == '.') {
                token = Token.LITERAL_INT;
                return;
            }
            sp++;
            ch = charAt(++bp);
            isDouble = true;

            for (;;) {
                if (ch >= '0' && ch <= '9') {
                    sp++;
                } else {
                    break;
                }
                ch = charAt(++bp);
            }
        }

        if (ch == 'e' || ch == 'E') {
            sp++;
            ch = charAt(++bp);

            if (ch == '+' || ch == '-') {
                sp++;
                ch = charAt(++bp);
            }

            for (;;) {
                if (ch >= '0' && ch <= '9') {
                    sp++;
                } else {
                    break;
                }
                ch = charAt(++bp);
            }

            isDouble = true;
        }

        if (ch == 'f' || ch == 'F') {
            token = Token.BINARY_FLOAT;
            scanChar();
            return;
        }

        if (ch == 'd' || ch == 'D') {
            token = Token.BINARY_DOUBLE;
            scanChar();
            return;
        }

        if (isDouble) {
            token = Token.LITERAL_FLOAT;
        } else {
            token = Token.LITERAL_INT;
        }
    }

}
