var prismic = prismic || {};
prismic.startExperiment = function(cxApi) {
  // https://developer.mozilla.org/en-US/docs/DOM/document.cookie
  var docCookies = {
    getItem: function(sKey) {
      return decodeURIComponent(document.cookie.replace(new RegExp("(?:(?:^|.*;)\\s*" + encodeURIComponent(sKey).replace(/[\-\.\+\*]/g, "\\$&") + "\\s*\\=\\s*([^;]*).*$)|^.*$"), "$1")) || null;
    },
    setItem: function(sKey, sValue, vEnd, sPath, sDomain, bSecure) {
      if (!sKey || /^(?:expires|max\-age|path|domain|secure)$/i.test(sKey)) {
        return false;
      }
      var sExpires = "";
      if (vEnd) {
        switch (vEnd.constructor) {
          case Number:
            sExpires = vEnd === Infinity ? "; expires=Fri, 31 Dec 9999 23:59:59 GMT" : "; max-age=" + vEnd;
            break;
          case String:
            sExpires = "; expires=" + vEnd;
            break;
          case Date:
            sExpires = "; expires=" + vEnd.toUTCString();
            break;
        }
      }
      document.cookie = encodeURIComponent(sKey) + "=" + encodeURIComponent(sValue) + sExpires + (sDomain ? "; domain=" + sDomain : "") + (sPath ? "; path=" + sPath : "") + (bSecure ? "; secure" : "");
      return true;
    }
  };

  var cookieKey = 'prismic.experiment-variation';

  function storeVariationAndReload(variation) {
    docCookies.setItem(cookieKey, variation, 60 * 60 * 24 * 30, "/");
    location.reload();
  }

  var variationInCookie = docCookies.getItem(cookieKey);
  var googleVariation = cxApi.getChosenVariation();
  console.log(variationInCookie, 'in cookie');
  console.log(googleVariation, 'in google');
  if (variationInCookie && variationInCookie != -1) {
    if (variationInCookie != googleVariation) {
      storeVariationAndReload(googleVariation);
    }
  } else {
    var chosenVariation = cxApi.chooseVariation();
    if (chosenVariation != -1) {
      storeVariationAndReload(chosenVariation);
    }
  }
};
