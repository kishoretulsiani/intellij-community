// Generated by JFlex 1.9.0 http://jflex.de/  (tweaked for IntelliJ platform)
// source: _JavaDocLexer.flex

  /* It's an automatically generated code. Do not modify it. */
package com.intellij.lang.java.lexer;

import com.intellij.lexer.DocCommentTokenTypes;
import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

@SuppressWarnings("ALL")

class _JavaDocLexer implements FlexLexer {

  /** This character denotes the end of file */
  public static final int YYEOF = -1;

  /** initial size of the lookahead buffer */
  private static final int ZZ_BUFFERSIZE = 16384;

  /** lexical states */
  public static final int YYINITIAL = 0;
  public static final int COMMENT_DATA_START = 2;
  public static final int COMMENT_DATA = 4;
  public static final int TAG_DOC_SPACE = 6;
  public static final int PARAM_TAG_SPACE = 8;
  public static final int DOC_TAG_VALUE = 10;
  public static final int DOC_TAG_VALUE_IN_PAREN = 12;
  public static final int DOC_TAG_VALUE_IN_LTGT = 14;
  public static final int INLINE_TAG_NAME = 16;
  public static final int CODE_TAG = 18;
  public static final int CODE_TAG_SPACE = 20;
  public static final int SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON = 22;
  public static final int SNIPPET_TAG_BODY_DATA = 24;
  public static final int SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES = 26;
  public static final int SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES = 28;

  /**
   * ZZ_LEXSTATE[l] is the state in the DFA for the lexical state l
   * ZZ_LEXSTATE[l+1] is the state in the DFA for the lexical state l
   *                  at the beginning of a line
   * l is of the form l = 2*k, k a non negative integer
   */
  private static final int ZZ_LEXSTATE[] = {
     0,  0,  1,  1,  2,  2,  3,  3,  4,  4,  5,  5,  6,  6,  7,  7, 
     8,  8,  9,  9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14
  };

  /**
   * Top-level table for translating characters to character classes
   */
  private static final int [] ZZ_CMAP_TOP = zzUnpackcmap_top();

  private static final String ZZ_CMAP_TOP_PACKED_0 =
    "\1\0\1\u0100\1\u0200\1\u0300\1\u0400\1\u0500\1\u0600\1\u0700"+
    "\1\u0800\1\u0900\1\u0a00\1\u0b00\1\u0c00\1\u0d00\1\u0e00\1\u0f00"+
    "\1\u1000\1\u0100\1\u1100\1\u1200\1\u1300\1\u0100\1\u1400\1\u1500"+
    "\1\u1600\1\u1700\1\u1800\1\u1900\1\u1a00\1\u1b00\1\u0100\1\u1c00"+
    "\1\u1d00\1\u1e00\12\u1f00\1\u2000\1\u2100\1\u2200\1\u1f00\1\u2300"+
    "\1\u2400\2\u1f00\31\u0100\1\u1b00\121\u0100\1\u2500\4\u0100\1\u2600"+
    "\1\u0100\1\u2700\1\u2800\1\u2900\1\u2a00\1\u2b00\1\u2c00\53\u0100"+
    "\1\u2d00\10\u2e00\31\u1f00\1\u0100\1\u2f00\1\u3000\1\u0100\1\u3100"+
    "\1\u3200\1\u3300\1\u3400\1\u3500\1\u3600\1\u3700\1\u3800\1\u3900"+
    "\1\u0100\1\u3a00\1\u3b00\1\u3c00\1\u3d00\1\u3e00\1\u3f00\1\u4000"+
    "\1\u4100\1\u4200\1\u4300\1\u4400\1\u4500\1\u4600\1\u4700\1\u4800"+
    "\1\u4900\1\u4a00\1\u4b00\1\u4c00\1\u4d00\1\u1f00\1\u4e00\1\u4f00"+
    "\1\u5000\1\u5100\3\u0100\1\u5200\1\u5300\1\u5400\12\u1f00\4\u0100"+
    "\1\u5500\17\u1f00\2\u0100\1\u5600\41\u1f00\2\u0100\1\u5700\1\u5800"+
    "\2\u1f00\1\u5900\1\u5a00\27\u0100\1\u5b00\4\u0100\1\u5c00\1\u5d00"+
    "\42\u1f00\1\u0100\1\u5e00\1\u5f00\11\u1f00\1\u6000\27\u1f00\1\u6100"+
    "\1\u6200\1\u6300\1\u6400\11\u1f00\1\u6500\1\u6600\5\u1f00\1\u6700"+
    "\1\u6800\2\u1f00\1\u6900\1\u1f00\1\u6a00\21\u1f00\246\u0100\1\u6b00"+
    "\20\u0100\1\u6c00\1\u6d00\25\u0100\1\u6e00\34\u0100\1\u6f00\14\u1f00"+
    "\2\u0100\1\u7000\5\u1f00\23\u0100\1\u7100\u0dec\u1f00";

  private static int [] zzUnpackcmap_top() {
    int [] result = new int[4352];
    int offset = 0;
    offset = zzUnpackcmap_top(ZZ_CMAP_TOP_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackcmap_top(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /**
   * Second-level tables for translating characters to character classes
   */
  private static final int [] ZZ_CMAP_BLOCKS = zzUnpackcmap_blocks();

  private static final String ZZ_CMAP_BLOCKS_PACKED_0 =
    "\11\0\1\1\1\2\1\3\1\4\1\5\22\0\1\1"+
    "\1\0\1\6\1\7\1\10\2\0\1\11\1\12\1\13"+
    "\1\14\1\0\1\15\1\16\1\17\1\20\12\21\1\22"+
    "\1\0\1\23\1\24\1\25\1\0\1\26\32\10\1\27"+
    "\1\0\1\27\1\0\1\10\1\0\1\30\1\10\1\31"+
    "\1\32\1\33\3\10\1\34\2\10\1\35\1\36\1\37"+
    "\1\40\1\41\1\10\1\42\1\43\1\44\6\10\1\45"+
    "\1\0\1\46\7\0\1\3\34\0\4\10\4\0\1\10"+
    "\12\0\1\10\4\0\1\10\5\0\27\10\1\0\37\10"+
    "\1\0\u01ca\10\4\0\14\10\16\0\5\10\7\0\1\10"+
    "\1\0\1\10\201\0\5\10\1\0\2\10\2\0\4\10"+
    "\1\0\1\10\6\0\1\10\1\0\3\10\1\0\1\10"+
    "\1\0\24\10\1\0\123\10\1\0\213\10\10\0\246\10"+
    "\1\0\46\10\2\0\1\10\6\0\51\10\6\0\1\10"+
    "\100\0\33\10\4\0\4\10\30\0\1\10\24\0\53\10"+
    "\43\0\2\10\1\0\143\10\1\0\1\10\17\0\2\10"+
    "\7\0\2\10\12\0\3\10\2\0\1\10\20\0\1\10"+
    "\1\0\36\10\35\0\131\10\13\0\1\10\30\0\41\10"+
    "\11\0\2\10\4\0\1\10\3\0\30\10\4\0\1\10"+
    "\11\0\1\10\3\0\1\10\27\0\31\10\7\0\13\10"+
    "\65\0\25\10\1\0\22\10\74\0\66\10\3\0\1\10"+
    "\22\0\1\10\7\0\12\10\17\0\20\10\4\0\10\10"+
    "\2\0\2\10\2\0\26\10\1\0\7\10\1\0\1\10"+
    "\3\0\4\10\3\0\1\10\20\0\1\10\15\0\2\10"+
    "\1\0\3\10\16\0\4\10\7\0\2\10\10\0\6\10"+
    "\4\0\2\10\2\0\26\10\1\0\7\10\1\0\2\10"+
    "\1\0\2\10\1\0\2\10\37\0\4\10\1\0\1\10"+
    "\23\0\3\10\20\0\11\10\1\0\3\10\1\0\26\10"+
    "\1\0\7\10\1\0\2\10\1\0\5\10\3\0\1\10"+
    "\22\0\1\10\17\0\2\10\17\0\1\10\7\0\1\10"+
    "\13\0\10\10\2\0\2\10\2\0\26\10\1\0\7\10"+
    "\1\0\2\10\1\0\5\10\3\0\1\10\36\0\2\10"+
    "\1\0\3\10\17\0\1\10\21\0\1\10\1\0\6\10"+
    "\3\0\3\10\1\0\4\10\3\0\2\10\1\0\1\10"+
    "\1\0\2\10\3\0\2\10\3\0\3\10\3\0\14\10"+
    "\26\0\1\10\50\0\1\10\13\0\10\10\1\0\3\10"+
    "\1\0\27\10\1\0\20\10\3\0\1\10\32\0\3\10"+
    "\5\0\2\10\36\0\1\10\4\0\10\10\1\0\3\10"+
    "\1\0\27\10\1\0\12\10\1\0\5\10\3\0\1\10"+
    "\40\0\1\10\1\0\2\10\17\0\2\10\21\0\11\10"+
    "\1\0\3\10\1\0\51\10\2\0\1\10\20\0\1\10"+
    "\5\0\3\10\10\0\3\10\30\0\6\10\5\0\22\10"+
    "\3\0\30\10\1\0\11\10\1\0\1\10\2\0\7\10"+
    "\72\0\60\10\1\0\2\10\13\0\10\10\72\0\2\10"+
    "\1\0\1\10\1\0\5\10\1\0\30\10\1\0\1\10"+
    "\1\0\12\10\1\0\2\10\11\0\1\10\2\0\5\10"+
    "\1\0\1\10\25\0\4\10\40\0\1\10\77\0\10\10"+
    "\1\0\44\10\33\0\5\10\163\0\53\10\24\0\1\10"+
    "\20\0\6\10\4\0\4\10\3\0\1\10\3\0\2\10"+
    "\7\0\3\10\4\0\15\10\14\0\1\10\21\0\46\10"+
    "\1\0\1\10\5\0\1\10\2\0\53\10\1\0\115\10"+
    "\1\0\4\10\2\0\7\10\1\0\1\10\1\0\4\10"+
    "\2\0\51\10\1\0\4\10\2\0\41\10\1\0\4\10"+
    "\2\0\7\10\1\0\1\10\1\0\4\10\2\0\17\10"+
    "\1\0\71\10\1\0\4\10\2\0\103\10\45\0\20\10"+
    "\20\0\126\10\2\0\6\10\3\0\u016c\10\2\0\21\10"+
    "\1\0\32\10\5\0\113\10\3\0\13\10\7\0\15\10"+
    "\1\0\4\10\16\0\22\10\16\0\22\10\16\0\15\10"+
    "\1\0\3\10\17\0\64\10\43\0\1\10\3\0\2\10"+
    "\103\0\131\10\7\0\5\10\2\0\42\10\1\0\1\10"+
    "\5\0\106\10\12\0\37\10\61\0\36\10\2\0\5\10"+
    "\13\0\54\10\4\0\32\10\66\0\27\10\11\0\65\10"+
    "\122\0\1\10\135\0\57\10\21\0\7\10\67\0\36\10"+
    "\15\0\2\10\12\0\54\10\32\0\44\10\51\0\3\10"+
    "\12\0\44\10\2\0\11\10\7\0\53\10\2\0\3\10"+
    "\51\0\4\10\1\0\6\10\1\0\2\10\3\0\1\10"+
    "\5\0\300\10\100\0\26\10\2\0\6\10\2\0\46\10"+
    "\2\0\6\10\2\0\10\10\1\0\1\10\1\0\1\10"+
    "\1\0\1\10\1\0\37\10\2\0\65\10\1\0\7\10"+
    "\1\0\1\10\3\0\3\10\1\0\7\10\3\0\4\10"+
    "\2\0\6\10\4\0\15\10\5\0\3\10\1\0\7\10"+
    "\53\0\2\3\25\0\2\10\23\0\1\10\34\0\1\10"+
    "\15\0\1\10\20\0\15\10\3\0\40\10\102\0\1\10"+
    "\4\0\1\10\2\0\12\10\1\0\1\10\3\0\5\10"+
    "\6\0\1\10\1\0\1\10\1\0\1\10\1\0\4\10"+
    "\1\0\13\10\2\0\4\10\5\0\5\10\4\0\1\10"+
    "\21\0\51\10\u0177\0\57\10\1\0\57\10\1\0\205\10"+
    "\6\0\4\10\3\0\2\10\14\0\46\10\1\0\1\10"+
    "\5\0\1\10\2\0\70\10\7\0\1\10\20\0\27\10"+
    "\11\0\7\10\1\0\7\10\1\0\7\10\1\0\7\10"+
    "\1\0\7\10\1\0\7\10\1\0\7\10\1\0\7\10"+
    "\120\0\1\10\325\0\3\10\31\0\11\10\7\0\5\10"+
    "\2\0\5\10\4\0\126\10\6\0\3\10\1\0\132\10"+
    "\1\0\4\10\5\0\53\10\1\0\136\10\21\0\40\10"+
    "\60\0\u010d\10\3\0\215\10\103\0\56\10\2\0\15\10"+
    "\3\0\20\10\12\0\2\10\24\0\57\10\20\0\37\10"+
    "\2\0\120\10\47\0\11\10\2\0\147\10\2\0\65\10"+
    "\2\0\11\10\52\0\15\10\1\0\3\10\1\0\4\10"+
    "\1\0\27\10\25\0\1\10\7\0\64\10\16\0\62\10"+
    "\76\0\6\10\3\0\1\10\1\0\2\10\13\0\34\10"+
    "\12\0\27\10\31\0\35\10\7\0\57\10\34\0\1\10"+
    "\20\0\5\10\1\0\12\10\12\0\5\10\1\0\51\10"+
    "\27\0\3\10\1\0\10\10\24\0\27\10\3\0\1\10"+
    "\3\0\62\10\1\0\1\10\3\0\2\10\2\0\5\10"+
    "\2\0\1\10\1\0\1\10\30\0\3\10\2\0\13\10"+
    "\7\0\3\10\14\0\6\10\2\0\6\10\2\0\6\10"+
    "\11\0\7\10\1\0\7\10\1\0\53\10\1\0\16\10"+
    "\6\0\163\10\35\0\244\10\14\0\27\10\4\0\61\10"+
    "\4\0\u0100\3\156\10\2\0\152\10\46\0\7\10\14\0"+
    "\5\10\5\0\1\10\1\0\12\10\1\0\15\10\1\0"+
    "\5\10\1\0\1\10\1\0\2\10\1\0\2\10\1\0"+
    "\154\10\41\0\153\10\22\0\100\10\2\0\66\10\50\0"+
    "\15\10\66\0\2\10\30\0\3\10\31\0\1\10\6\0"+
    "\5\10\1\0\207\10\7\0\1\10\34\0\32\10\4\0"+
    "\1\10\1\0\32\10\13\0\131\10\3\0\6\10\2\0"+
    "\6\10\2\0\6\10\2\0\3\10\3\0\2\10\3\0"+
    "\2\10\31\0\14\10\1\0\32\10\1\0\23\10\1\0"+
    "\2\10\1\0\17\10\2\0\16\10\42\0\173\10\105\0"+
    "\65\10\u010b\0\35\10\3\0\61\10\57\0\40\10\15\0"+
    "\36\10\5\0\46\10\12\0\36\10\2\0\44\10\4\0"+
    "\10\10\1\0\5\10\52\0\236\10\22\0\44\10\4\0"+
    "\44\10\4\0\50\10\10\0\64\10\234\0\67\10\11\0"+
    "\26\10\12\0\10\10\230\0\6\10\2\0\1\10\1\0"+
    "\54\10\1\0\2\10\3\0\1\10\2\0\27\10\12\0"+
    "\27\10\11\0\37\10\101\0\23\10\1\0\2\10\12\0"+
    "\26\10\12\0\32\10\106\0\70\10\6\0\2\10\100\0"+
    "\1\10\17\0\4\10\1\0\3\10\1\0\35\10\52\0"+
    "\35\10\3\0\35\10\43\0\10\10\1\0\34\10\33\0"+
    "\66\10\12\0\26\10\12\0\23\10\15\0\22\10\156\0"+
    "\111\10\67\0\63\10\15\0\63\10\15\0\44\10\u015c\0"+
    "\52\10\6\0\2\10\116\0\35\10\12\0\1\10\10\0"+
    "\26\10\152\0\25\10\33\0\27\10\14\0\65\10\113\0"+
    "\55\10\40\0\31\10\32\0\44\10\35\0\1\10\2\0"+
    "\1\10\10\0\43\10\3\0\1\10\14\0\60\10\16\0"+
    "\4\10\25\0\1\10\1\0\1\10\43\0\22\10\1\0"+
    "\31\10\124\0\7\10\1\0\1\10\1\0\4\10\1\0"+
    "\17\10\1\0\12\10\7\0\57\10\46\0\10\10\2\0"+
    "\2\10\2\0\26\10\1\0\7\10\1\0\2\10\1\0"+
    "\5\10\3\0\1\10\22\0\1\10\14\0\5\10\236\0"+
    "\65\10\22\0\4\10\24\0\3\10\36\0\60\10\24\0"+
    "\2\10\1\0\1\10\270\0\57\10\51\0\4\10\44\0"+
    "\60\10\24\0\1\10\73\0\53\10\15\0\1\10\107\0"+
    "\33\10\345\0\54\10\164\0\100\10\37\0\10\10\2\0"+
    "\1\10\2\0\10\10\1\0\2\10\1\0\30\10\17\0"+
    "\1\10\1\0\1\10\136\0\10\10\2\0\47\10\20\0"+
    "\1\10\1\0\1\10\34\0\1\10\12\0\50\10\7\0"+
    "\1\10\25\0\1\10\13\0\56\10\23\0\1\10\42\0"+
    "\71\10\7\0\11\10\1\0\45\10\21\0\1\10\61\0"+
    "\36\10\160\0\7\10\1\0\2\10\1\0\46\10\25\0"+
    "\1\10\31\0\6\10\1\0\2\10\1\0\40\10\16\0"+
    "\1\10\u0147\0\23\10\275\0\1\10\54\0\4\10\37\0"+
    "\232\10\146\0\157\10\21\0\304\10\274\0\57\10\321\0"+
    "\107\10\271\0\71\10\7\0\37\10\161\0\36\10\22\0"+
    "\60\10\20\0\4\10\37\0\25\10\5\0\23\10\260\0"+
    "\100\10\200\0\113\10\5\0\1\10\102\0\15\10\100\0"+
    "\2\10\1\0\1\10\34\0\370\10\10\0\326\10\52\0"+
    "\11\10\367\0\37\10\61\0\3\10\21\0\4\10\10\0"+
    "\u018c\10\4\0\153\10\5\0\15\10\3\0\11\10\7\0"+
    "\12\10\146\0\125\10\1\0\107\10\1\0\2\10\2\0"+
    "\1\10\2\0\2\10\2\0\4\10\1\0\14\10\1\0"+
    "\1\10\1\0\7\10\1\0\101\10\1\0\4\10\2\0"+
    "\10\10\1\0\7\10\1\0\34\10\1\0\4\10\1\0"+
    "\5\10\1\0\1\10\3\0\7\10\1\0\u0154\10\2\0"+
    "\31\10\1\0\31\10\1\0\37\10\1\0\31\10\1\0"+
    "\37\10\1\0\31\10\1\0\37\10\1\0\31\10\1\0"+
    "\37\10\1\0\31\10\1\0\10\10\64\0\55\10\12\0"+
    "\7\10\20\0\1\10\u0171\0\54\10\23\0\306\10\73\0"+
    "\104\10\7\0\1\10\u0164\0\1\10\117\0\4\10\1\0"+
    "\33\10\1\0\2\10\1\0\1\10\2\0\1\10\1\0"+
    "\12\10\1\0\4\10\1\0\1\10\1\0\1\10\6\0"+
    "\1\10\4\0\1\10\1\0\1\10\1\0\1\10\1\0"+
    "\3\10\1\0\2\10\1\0\1\10\2\0\1\10\1\0"+
    "\1\10\1\0\1\10\1\0\1\10\1\0\1\10\1\0"+
    "\2\10\1\0\1\10\2\0\4\10\1\0\7\10\1\0"+
    "\4\10\1\0\4\10\1\0\1\10\1\0\12\10\1\0"+
    "\21\10\5\0\3\10\1\0\5\10\1\0\21\10\104\0"+
    "\336\10\42\0\65\10\13\0\336\10\2\0\u0182\10\16\0"+
    "\u0131\10\37\0\36\10\342\0\113\10\265\0";

  private static int [] zzUnpackcmap_blocks() {
    int [] result = new int[29184];
    int offset = 0;
    offset = zzUnpackcmap_blocks(ZZ_CMAP_BLOCKS_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackcmap_blocks(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }

  /**
   * Translates DFA states to action switch labels.
   */
  private static final int [] ZZ_ACTION = zzUnpackAction();

  private static final String ZZ_ACTION_PACKED_0 =
    "\17\0\3\1\1\2\1\3\2\2\1\4\1\5\1\6"+
    "\1\7\1\10\1\11\1\12\1\13\1\14\1\15\1\16"+
    "\1\17\1\13\1\20\1\1\2\21\1\22\1\21\1\23"+
    "\1\24\1\13\1\25\1\26\1\27\1\13\1\6\1\3"+
    "\1\30\1\31\1\13\1\3\1\32\1\5\1\13\1\3"+
    "\1\5\1\0\1\33\1\0\7\34\1\35\13\34\1\36"+
    "\3\34\1\37\1\34\1\37\3\34\1\40";

  private static int [] zzUnpackAction() {
    int [] result = new int[92];
    int offset = 0;
    offset = zzUnpackAction(ZZ_ACTION_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAction(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /**
   * Translates a state to a row index in the transition table
   */
  private static final int [] ZZ_ROWMAP = zzUnpackRowMap();

  private static final String ZZ_ROWMAP_PACKED_0 =
    "\0\0\0\47\0\116\0\165\0\234\0\303\0\352\0\u0111"+
    "\0\u0138\0\u015f\0\u0186\0\u01ad\0\u01d4\0\u01fb\0\u0222\0\u0249"+
    "\0\u0270\0\u0297\0\u0249\0\u02be\0\u0270\0\u02e5\0\u0249\0\u0249"+
    "\0\u030c\0\u0333\0\u035a\0\u0381\0\u0249\0\u03a8\0\u0249\0\u0249"+
    "\0\u0249\0\u0249\0\u03cf\0\u0249\0\u03f6\0\u0249\0\u041d\0\u041d"+
    "\0\u0270\0\u0249\0\u0249\0\u0444\0\u0249\0\u0270\0\u0249\0\u0249"+
    "\0\u046b\0\u0492\0\u0249\0\u0249\0\u04b9\0\u04e0\0\u0249\0\u04b9"+
    "\0\u0507\0\u052e\0\u0507\0\u0270\0\u0249\0\u0555\0\u057c\0\u05a3"+
    "\0\u05ca\0\u05f1\0\u0618\0\u063f\0\u0666\0\u0249\0\u068d\0\u06b4"+
    "\0\u06db\0\u0702\0\u0729\0\u0750\0\u0777\0\u079e\0\u07c5\0\u07ec"+
    "\0\u0813\0\u05ca\0\u083a\0\u0861\0\u0888\0\u057c\0\u08af\0\u05ca"+
    "\0\u08d6\0\u08fd\0\u0924\0\u05ca";

  private static int [] zzUnpackRowMap() {
    int [] result = new int[92];
    int offset = 0;
    offset = zzUnpackRowMap(ZZ_ROWMAP_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackRowMap(String packed, int offset, int [] result) {
    int i = 0;  /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length() - 1;
    while (i < l) {
      int high = packed.charAt(i++) << 16;
      result[j++] = high | packed.charAt(i++);
    }
    return j;
  }

  /**
   * The transition table of the DFA
   */
  private static final int [] ZZ_TRANS = zzUnpacktrans();

  private static final String ZZ_TRANS_PACKED_0 =
    "\14\20\1\21\3\20\1\22\26\20\1\23\2\24\1\20"+
    "\2\24\6\23\1\25\11\23\1\26\16\23\1\27\1\30"+
    "\1\23\1\31\1\24\1\20\1\31\1\24\6\23\1\25"+
    "\30\23\1\27\1\30\1\20\2\32\1\20\2\32\6\20"+
    "\1\21\31\20\1\30\1\20\2\33\1\20\2\33\6\20"+
    "\1\21\32\20\1\23\2\34\1\20\2\34\1\23\1\35"+
    "\1\36\1\23\1\37\1\23\1\25\1\40\1\23\1\36"+
    "\1\23\1\36\1\23\1\41\3\23\16\36\1\23\1\30"+
    "\1\20\2\24\1\20\2\24\2\20\1\36\2\20\1\42"+
    "\1\21\1\40\1\20\1\36\1\20\1\36\5\20\16\36"+
    "\12\20\1\43\3\20\1\21\10\20\1\44\2\20\15\43"+
    "\16\20\1\21\11\20\1\45\20\20\1\46\1\47\1\50"+
    "\1\20\2\50\6\46\1\51\30\46\1\27\1\30\1\46"+
    "\1\47\1\50\1\20\2\50\6\46\1\51\31\46\1\30"+
    "\1\52\2\24\1\52\2\24\1\53\1\52\1\54\1\55"+
    "\2\52\1\56\5\52\1\57\1\52\1\60\3\52\15\54"+
    "\1\52\1\30\1\61\1\62\1\24\1\61\2\62\37\61"+
    "\1\63\1\64\1\65\1\66\1\24\1\65\2\66\1\67"+
    "\37\65\1\70\1\71\1\72\1\24\1\71\2\72\3\71"+
    "\1\67\34\71\1\73\63\0\1\74\3\0\1\75\42\0"+
    "\1\76\33\0\2\24\1\0\2\24\41\0\1\77\2\0"+
    "\1\77\2\0\33\77\1\100\5\77\1\0\1\31\2\0"+
    "\1\31\43\0\2\32\1\0\2\32\42\0\2\33\1\0"+
    "\2\33\42\0\2\34\1\0\2\34\51\0\1\36\6\0"+
    "\1\36\1\0\1\36\5\0\16\36\12\0\1\43\5\0"+
    "\2\43\1\0\2\43\5\0\15\43\2\0\1\101\2\0"+
    "\1\101\2\0\23\101\1\102\3\101\1\103\3\101\1\104"+
    "\1\101\1\105\2\101\2\0\2\50\1\0\2\50\51\0"+
    "\1\54\10\0\1\54\6\0\15\54\2\0\2\61\1\0"+
    "\42\61\2\0\1\61\1\62\1\24\1\61\2\62\37\61"+
    "\2\0\2\65\1\0\3\65\1\0\41\65\1\66\1\24"+
    "\1\65\2\66\1\0\40\65\2\71\1\0\6\71\1\0"+
    "\36\71\1\72\1\24\1\71\2\72\3\71\1\0\35\71"+
    "\14\0\1\106\32\0\1\77\2\0\1\77\2\0\42\77"+
    "\2\0\1\77\2\0\22\77\1\107\16\77\1\101\2\0"+
    "\1\101\2\0\40\101\1\0\1\101\2\0\1\101\2\0"+
    "\32\101\1\110\5\101\1\0\1\101\2\0\1\101\2\0"+
    "\26\101\1\111\11\101\1\0\1\101\2\0\1\101\2\0"+
    "\22\101\1\112\15\101\1\0\1\101\2\0\1\101\2\0"+
    "\31\101\1\113\6\101\1\0\1\77\2\0\1\77\2\0"+
    "\34\77\1\114\4\77\1\101\2\0\1\101\2\0\24\101"+
    "\1\115\13\101\1\0\1\101\2\0\1\101\2\0\36\101"+
    "\1\116\1\101\1\0\1\101\2\0\1\101\2\0\34\101"+
    "\1\117\3\101\1\0\1\101\2\0\1\101\2\0\26\101"+
    "\1\120\11\101\1\0\1\77\2\0\1\77\2\0\22\77"+
    "\1\121\16\77\1\101\2\0\1\101\2\0\25\101\1\122"+
    "\12\101\1\0\1\101\2\0\1\101\2\0\25\101\1\123"+
    "\12\101\1\0\1\101\2\0\1\101\2\0\22\101\1\124"+
    "\15\101\1\0\1\101\2\0\1\101\2\0\33\101\1\125"+
    "\4\101\1\0\1\77\2\0\1\77\2\0\30\77\1\126"+
    "\10\77\1\101\2\0\1\101\2\0\34\101\1\127\3\101"+
    "\1\0\1\101\2\0\1\101\2\0\30\101\1\130\7\101"+
    "\1\0\1\101\2\0\1\101\2\0\33\101\1\131\4\101"+
    "\1\0\1\101\2\0\1\101\2\0\22\101\1\132\15\101"+
    "\1\0\1\101\2\0\1\101\2\0\25\101\1\133\12\101"+
    "\1\0\1\101\2\0\1\101\2\0\27\101\1\122\10\101"+
    "\1\0\1\101\2\0\1\101\2\0\36\101\1\134\1\101"+
    "\1\0";

  private static int [] zzUnpacktrans() {
    int [] result = new int[2379];
    int offset = 0;
    offset = zzUnpacktrans(ZZ_TRANS_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpacktrans(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      value--;
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /* error codes */
  private static final int ZZ_UNKNOWN_ERROR = 0;
  private static final int ZZ_NO_MATCH = 1;
  private static final int ZZ_PUSHBACK_2BIG = 2;

  /* error messages for the codes above */
  private static final String[] ZZ_ERROR_MSG = {
    "Unknown internal scanner error",
    "Error: could not match input",
    "Error: pushback value was too large"
  };

  /**
   * ZZ_ATTRIBUTE[aState] contains the attributes of state {@code aState}
   */
  private static final int [] ZZ_ATTRIBUTE = zzUnpackAttribute();

  private static final String ZZ_ATTRIBUTE_PACKED_0 =
    "\17\0\1\11\2\1\1\11\3\1\2\11\4\1\1\11"+
    "\1\1\4\11\1\1\1\11\1\1\1\11\3\1\2\11"+
    "\1\1\1\11\1\1\2\11\2\1\2\11\2\1\1\11"+
    "\4\1\1\0\1\11\1\0\7\1\1\11\26\1";

  private static int [] zzUnpackAttribute() {
    int [] result = new int[92];
    int offset = 0;
    offset = zzUnpackAttribute(ZZ_ATTRIBUTE_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAttribute(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }

  /** the input device */
  private java.io.Reader zzReader;

  /** the current state of the DFA */
  private int zzState;

  /** the current lexical state */
  private int zzLexicalState = YYINITIAL;

  /** this buffer contains the current text to be matched and is
      the source of the yytext() string */
  private CharSequence zzBuffer = "";

  /** the textposition at the last accepting state */
  private int zzMarkedPos;

  /** the current text position in the buffer */
  private int zzCurrentPos;

  /** startRead marks the beginning of the yytext() string in the buffer */
  private int zzStartRead;

  /** endRead marks the last character in the buffer, that has been read
      from input */
  private int zzEndRead;

  /** zzAtEOF == true <=> the scanner is at the EOF */
  private boolean zzAtEOF;

  /** Number of newlines encountered up to the start of the matched text. */
  @SuppressWarnings("unused")
  private int yyline;

  /** Number of characters from the last newline up to the start of the matched text. */
  @SuppressWarnings("unused")
  protected int yycolumn;

  /** Number of characters up to the start of the matched text. */
  @SuppressWarnings("unused")
  private long yychar;

  /** Whether the scanner is currently at the beginning of a line. */
  @SuppressWarnings("unused")
  private boolean zzAtBOL = true;

  /** Whether the user-EOF-code has already been executed. */
  @SuppressWarnings("unused")
  private boolean zzEOFDone;

  /* user code: */
  private boolean myJdk15Enabled;
  private DocCommentTokenTypes myTokenTypes;
  private int mySnippetBracesLevel = 0;

  public _JavaDocLexer(boolean isJdk15Enabled, DocCommentTokenTypes tokenTypes) {
    this((java.io.Reader)null);
    myJdk15Enabled = isJdk15Enabled;
    myTokenTypes = tokenTypes;
  }

  public boolean checkAhead(char c) {
    if (zzMarkedPos >= zzBuffer.length()) return false;
    return zzBuffer.charAt(zzMarkedPos) == c;
  }

  public void goTo(int offset) {
    zzCurrentPos = zzMarkedPos = zzStartRead = offset;
    zzAtEOF = false;
  }


  /**
   * Creates a new scanner
   *
   * @param   in  the java.io.Reader to read input from.
   */
  _JavaDocLexer(java.io.Reader in) {
    this.zzReader = in;
  }


  /** Returns the maximum size of the scanner buffer, which limits the size of tokens. */
  private int zzMaxBufferLen() {
    return Integer.MAX_VALUE;
  }

  /**  Whether the scanner buffer can grow to accommodate a larger token. */
  private boolean zzCanGrow() {
    return true;
  }

  /**
   * Translates raw input code points to DFA table row
   */
  private static int zzCMap(int input) {
    int offset = input & 255;
    return offset == input ? ZZ_CMAP_BLOCKS[offset] : ZZ_CMAP_BLOCKS[ZZ_CMAP_TOP[input >> 8] | offset];
  }

  public final int getTokenStart() {
    return zzStartRead;
  }

  public final int getTokenEnd() {
    return getTokenStart() + yylength();
  }

  public void reset(CharSequence buffer, int start, int end, int initialState) {
    zzBuffer = buffer;
    zzCurrentPos = zzMarkedPos = zzStartRead = start;
    zzAtEOF  = false;
    zzAtBOL = true;
    zzEndRead = end;
    yybegin(initialState);
  }

  /**
   * Refills the input buffer.
   *
   * @return      {@code false}, iff there was new input.
   *
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  private boolean zzRefill() throws java.io.IOException {
    return true;
  }


  /**
   * Returns the current lexical state.
   */
  public final int yystate() {
    return zzLexicalState;
  }


  /**
   * Enters a new lexical state
   *
   * @param newState the new lexical state
   */
  public final void yybegin(int newState) {
    zzLexicalState = newState;
  }


  /**
   * Returns the text matched by the current regular expression.
   */
  public final CharSequence yytext() {
    return zzBuffer.subSequence(zzStartRead, zzMarkedPos);
  }


  /**
   * Returns the character at position {@code pos} from the
   * matched text.
   *
   * It is equivalent to yytext().charAt(pos), but faster
   *
   * @param pos the position of the character to fetch.
   *            A value from 0 to yylength()-1.
   *
   * @return the character at position pos
   */
  public final char yycharat(int pos) {
    return zzBuffer.charAt(zzStartRead+pos);
  }


  /**
   * Returns the length of the matched text region.
   */
  public final int yylength() {
    return zzMarkedPos-zzStartRead;
  }


  /**
   * Reports an error that occurred while scanning.
   *
   * In a wellformed scanner (no or only correct usage of
   * yypushback(int) and a match-all fallback rule) this method
   * will only be called with things that "Can't Possibly Happen".
   * If this method is called, something is seriously wrong
   * (e.g. a JFlex bug producing a faulty scanner etc.).
   *
   * Usual syntax/scanner level error handling should be done
   * in error fallback rules.
   *
   * @param   errorCode  the code of the errormessage to display
   */
  private void zzScanError(int errorCode) {
    String message;
    try {
      message = ZZ_ERROR_MSG[errorCode];
    }
    catch (ArrayIndexOutOfBoundsException e) {
      message = ZZ_ERROR_MSG[ZZ_UNKNOWN_ERROR];
    }

    throw new Error(message);
  }


  /**
   * Pushes the specified amount of characters back into the input stream.
   *
   * They will be read again by then next call of the scanning method
   *
   * @param number  the number of characters to be read again.
   *                This number must not be greater than yylength()!
   */
  public void yypushback(int number)  {
    if ( number > yylength() )
      zzScanError(ZZ_PUSHBACK_2BIG);

    zzMarkedPos -= number;
  }


  /**
   * Resumes scanning until the next regular expression is matched,
   * the end of input is encountered or an I/O-Error occurs.
   *
   * @return      the next token
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  public IElementType advance() throws java.io.IOException
  {
    int zzInput;
    int zzAction;

    // cached fields:
    int zzCurrentPosL;
    int zzMarkedPosL;
    int zzEndReadL = zzEndRead;
    CharSequence zzBufferL = zzBuffer;

    int [] zzTransL = ZZ_TRANS;
    int [] zzRowMapL = ZZ_ROWMAP;
    int [] zzAttrL = ZZ_ATTRIBUTE;

    while (true) {
      zzMarkedPosL = zzMarkedPos;

      zzAction = -1;

      zzCurrentPosL = zzCurrentPos = zzStartRead = zzMarkedPosL;

      zzState = ZZ_LEXSTATE[zzLexicalState];

      // set up zzAction for empty match case:
      int zzAttributes = zzAttrL[zzState];
      if ( (zzAttributes & 1) == 1 ) {
        zzAction = zzState;
      }


      zzForAction: {
        while (true) {

          if (zzCurrentPosL < zzEndReadL) {
            zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL);
            zzCurrentPosL += Character.charCount(zzInput);
          }
          else if (zzAtEOF) {
            zzInput = YYEOF;
            break zzForAction;
          }
          else {
            // store back cached positions
            zzCurrentPos  = zzCurrentPosL;
            zzMarkedPos   = zzMarkedPosL;
            boolean eof = zzRefill();
            // get translated positions and possibly new buffer
            zzCurrentPosL  = zzCurrentPos;
            zzMarkedPosL   = zzMarkedPos;
            zzBufferL      = zzBuffer;
            zzEndReadL     = zzEndRead;
            if (eof) {
              zzInput = YYEOF;
              break zzForAction;
            }
            else {
              zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL);
              zzCurrentPosL += Character.charCount(zzInput);
            }
          }
          int zzNext = zzTransL[ zzRowMapL[zzState] + zzCMap(zzInput) ];
          if (zzNext == -1) break zzForAction;
          zzState = zzNext;

          zzAttributes = zzAttrL[zzState];
          if ( (zzAttributes & 1) == 1 ) {
            zzAction = zzState;
            zzMarkedPosL = zzCurrentPosL;
            if ( (zzAttributes & 8) == 8 ) break zzForAction;
          }

        }
      }

      // store back cached position
      zzMarkedPos = zzMarkedPosL;

      if (zzInput == YYEOF && zzStartRead == zzCurrentPos) {
        zzAtEOF = true;
        return null;
      }
      else {
        switch (zzAction < 0 ? zzAction : ZZ_ACTION[zzAction]) {
          case 1:
            { return myTokenTypes.badCharacter();
            }
          // fall through
          case 33: break;
          case 2:
            { yybegin(COMMENT_DATA); return myTokenTypes.commentData();
            }
          // fall through
          case 34: break;
          case 3:
            { return myTokenTypes.space();
            }
          // fall through
          case 35: break;
          case 4:
            { if (checkAhead('@')) {
    yybegin(INLINE_TAG_NAME);
    return myTokenTypes.inlineTagStart();
  }
  else {
    yybegin(COMMENT_DATA);
    return myTokenTypes.inlineTagStart();
  }
            }
          // fall through
          case 36: break;
          case 5:
            { yybegin(COMMENT_DATA); return myTokenTypes.inlineTagEnd();
            }
          // fall through
          case 37: break;
          case 6:
            { return myTokenTypes.commentData();
            }
          // fall through
          case 38: break;
          case 7:
            { if (checkAhead('<') || checkAhead('\"')) yybegin(COMMENT_DATA);
  else if (checkAhead('\u007b')) yybegin(COMMENT_DATA);  // lbrace - there's a error in JLex when typing lbrace directly
  else yybegin(DOC_TAG_VALUE);
  return myTokenTypes.space();
            }
          // fall through
          case 39: break;
          case 8:
            { yybegin(DOC_TAG_VALUE); return myTokenTypes.space();
            }
          // fall through
          case 40: break;
          case 9:
            { yybegin(COMMENT_DATA); return myTokenTypes.space();
            }
          // fall through
          case 41: break;
          case 10:
            { return myTokenTypes.tagValueSharp();
            }
          // fall through
          case 42: break;
          case 11:
            { return myTokenTypes.tagValueToken();
            }
          // fall through
          case 43: break;
          case 12:
            { yybegin(DOC_TAG_VALUE_IN_PAREN); return myTokenTypes.tagValueLParen();
            }
          // fall through
          case 44: break;
          case 13:
            { return myTokenTypes.tagValueComma();
            }
          // fall through
          case 45: break;
          case 14:
            { if (myJdk15Enabled) {
    yybegin(DOC_TAG_VALUE_IN_LTGT);
    return myTokenTypes.tagValueLT();
  }
  else {
    yybegin(COMMENT_DATA);
    return myTokenTypes.commentData();
  }
            }
          // fall through
          case 46: break;
          case 15:
            { yybegin(DOC_TAG_VALUE); return myTokenTypes.tagValueRParen();
            }
          // fall through
          case 47: break;
          case 16:
            { yybegin(COMMENT_DATA); return myTokenTypes.tagValueGT();
            }
          // fall through
          case 48: break;
          case 17:
            { yybegin(CODE_TAG); return myTokenTypes.commentData();
            }
          // fall through
          case 49: break;
          case 18:
            { yybegin(CODE_TAG); return myTokenTypes.space();
            }
          // fall through
          case 50: break;
          case 19:
            { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return myTokenTypes.commentData();
            }
          // fall through
          case 51: break;
          case 20:
            { yybegin(SNIPPET_ATTRIBUTE_VALUE_DOUBLE_QUOTES); return myTokenTypes.tagValueQuote();
            }
          // fall through
          case 52: break;
          case 21:
            { yybegin(SNIPPET_ATTRIBUTE_VALUE_SINGLE_QUOTES); return myTokenTypes.tagValueQuote();
            }
          // fall through
          case 53: break;
          case 22:
            { return myTokenTypes.commentLeadingAsterisks();
            }
          // fall through
          case 54: break;
          case 23:
            { yybegin(SNIPPET_TAG_BODY_DATA); return myTokenTypes.tagValueColon();
            }
          // fall through
          case 55: break;
          case 24:
            { mySnippetBracesLevel++; return myTokenTypes.commentData();
            }
          // fall through
          case 56: break;
          case 25:
            { if (mySnippetBracesLevel > 0) {
          mySnippetBracesLevel--;
          return myTokenTypes.commentData();
        } else {
          yybegin(COMMENT_DATA);
          return myTokenTypes.inlineTagEnd();
        }
            }
          // fall through
          case 57: break;
          case 26:
            { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return myTokenTypes.tagValueQuote();
            }
          // fall through
          case 58: break;
          case 27:
            { return myTokenTypes.commentEnd();
            }
          // fall through
          case 59: break;
          case 28:
            { yybegin(TAG_DOC_SPACE); return myTokenTypes.tagName();
            }
          // fall through
          case 60: break;
          case 29:
            { yybegin(COMMENT_DATA_START); return myTokenTypes.commentStart();
            }
          // fall through
          case 61: break;
          case 30:
            { yybegin(CODE_TAG_SPACE); return myTokenTypes.tagName();
            }
          // fall through
          case 62: break;
          case 31:
            { yybegin(PARAM_TAG_SPACE); return myTokenTypes.tagName();
            }
          // fall through
          case 63: break;
          case 32:
            { yybegin(SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON); return myTokenTypes.tagName();
            }
          // fall through
          case 64: break;
          default:
            zzScanError(ZZ_NO_MATCH);
          }
      }
    }
  }


}
