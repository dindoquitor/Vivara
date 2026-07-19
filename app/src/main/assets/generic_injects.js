//download blobs support
if (!window.vivaraClicksListener) {
    window.vivaraClicksListener = function(e) {
        if (e.target.tagName.toUpperCase() == "A" && e.target.attributes.href.value.toLowerCase().startsWith("blob:")) {
            var fileName = e.target.download;
            var url = e.target.attributes.href.value;
            var xhr=new XMLHttpRequest();
            xhr.open('GET', e.target.attributes.href.value, true);
            xhr.responseType = 'blob';
            xhr.onload = function(e) {
                if (this.status == 200) {
                    var blob = this.response;
                    var reader = new FileReader();
                    reader.readAsDataURL(blob);
                    reader.onloadend = function() {
                        base64data = reader.result;
                        Vivara.takeBlobDownloadData(base64data, fileName, url, blob.type);
                    }
                }
            };
            xhr.send();
            e.stopPropagation();
            e.preventDefault();
        }
    };
    document.addEventListener("click", window.vivaraClicksListener);
}

// video playback control support
Object.defineProperty(HTMLMediaElement.prototype, 'playing', {
    get: function(){
        return !!(this.currentTime > 0 && !this.paused && !this.ended && this.readyState > 2);
    }
})

window.vivaraTogglePlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      if (media.playing) {
        media.pause();
      } else {
        media.play();
      }
  }
}

window.vivaraStopPlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      media.pause();
      media.currentTime = 0;
  }
}

window.vivaraRewind = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime -= 10;
    }
}

window.vivaraFastForward = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime += 10;
    }
}

// context menu support
window.addEventListener("touchstart", function(e) {
    window.VIVARA_activeElement = e.target;
    window.VIVARA_touchStartX = e.touches[0].clientX;
    window.VIVARA_touchStartY = e.touches[0].clientY;
});