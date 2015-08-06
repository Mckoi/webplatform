
//
// Various text formatters and parsers for times/dates and numerical values.
//

(function() {

  var TextUtils = Packages.com.mckoi.apihelper.TextUtils;
  var TimeZone  = Packages.java.util.TimeZone;

  //
  // Formats a timestamp in 'yyyy/MM/dd hh:mm:ss' format.
  //
  exports.formatLongDateTimeString = function(timestamp, time_zone) {
    if (!time_zone) time_zone = null;
    return String(TextUtils.formatLongDateTimeString(timestamp, time_zone));
  }

  //
  // Parses a date/time string from 'yyyy/MM/dd hh:mm:ss' format.
  //
  exports.formatDateTimeString = function(timestamp, time_zone) {
    if (!time_zone) time_zone = null;
    return String(TextUtils.formatDateTimeString(timestamp, time_zone));
  }

  //
  // Formats a timestamp in 'yyyy/MM/dd hh:mm' format.
  //
  exports.parseLongDateTimeString = function(string, time_zone) {
    if (!time_zone) time_zone = null;
    return TextUtils.parseLongDateTimeString(string, time_zone);
  }

  //
  // Parses a date/time string from 'yyyy/MM/dd hh:mm' format.
  //
  exports.parseDateTimeString = function(string, time_zone) {
    if (!time_zone) time_zone = null;
    return TextUtils.parseDateTimeString(string, time_zone);
  }

  //
  // Returns a TimeZone object for the given id, where the string identifies the
  // time zone. For example; 'PST'
  //
  exports.getTimeZone = function(id) {
    return TimeZone.getTimeZone(id);
  }

  //
  // Given a number, formats it as a human data size value. For example, 500
  // becomes "500  ", 42500 becomes "4.25 K", 5500000 becomes "5.5 M", etc.
  //
  exports.formatHumanDataSizeValue = function(number) {
    return String(TextUtils.formatHumanDataSizeValue(number));
  }

  //
  // Formats a long value representing a number of nanoseconds into a human
  // understandable form (for example, '400 ns', '50.7 ms', '15 mins', etc.
  //
  exports.formatTimeFrame = function(nanos) {
    return String(TextUtils.formatTimeFrame(nanos));
  }


  //
  // Pads n characters in the given string.
  //
  exports.pad = function(num) {
    var str = '                                ';
    var out = '';
    while (true) {
      if (num > 32) {
        num -= 32;
        out = out + str;
      }
      else {
        out = out + str.substring(0, num);
        return out;
      }
    }
  }

  //
  // Right aligns the given string within the size of the characters given,
  // assuming each character glyph is of a fixed width size.
  //
  exports.rightAlign = function(str, size) {
    return exports.pad(Math.max(0, size - str.length)) + str;
  }

  //
  // Left aligns the given string within the size of the characters given,
  // assuming each character glyph is of a fixed width size.
  //
  exports.leftAlign = function(str, size) {
    return str + exports.pad(Math.max(0, size - str.length));
  }

  //
  // Returns the number of code points necessary to encode the given character
  // in UTF-8 format.
  //
  exports.utf8CodeCountIn = function(ch) {
    var sz = 1;
    if (ch > 0x07F) { ++sz; } else { return sz; }
    if (ch > 0x07FF) { ++sz; } else { return sz; }
    if (ch > 0x0FFFF) { ++sz; } else { return sz; }
    if (ch > 0x01FFFFF) { ++sz; } else { return sz; }
    if (ch > 0x03FFFFFF) { ++sz; } else { return sz; }
    if (ch > 0x07FFFFFFF) { ++sz; } else { return sz; }
    return sz;
  }

})();
