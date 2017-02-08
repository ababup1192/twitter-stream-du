$(function () {
    var connect = function (attempt) {

        var connectionAttempt = attempt;

        var tweetSocket = new WebSocket(url);

        tweetSocket.onmessage = function (e) {
            console.log(e);
            var data = JSON.parse(e.data);
            $("#tweets").prepend('' +
            '<tr>' +
            '   <td class="col-sm-1 image">' +
            '      <a href="https://twitter.com/' + data.user.screen_name + '" target="_blank">' +
            '           <img src="' + data.user.profile_image_url_https + '"/>' +
            '      </a>' +
            '      <div class="popup"></div>' +
            '   </td>' +
            '<td class="alert alert-success col-sm-11">' + data.text + '</td>' +
            '</tr>');
        };

        tweetSocket.onopen = function () {
            connectionAttempt = 1;
            tweetSocket.send("subscribe");
        };

        tweetSocket.onclose = function () {
            if (connectionAttempt < 3) {
                $("#tweets").prepend('<p>WARNING: Lost Server connection, attempting to' +
                'reconnect. Attempt number ' + connectionAttempt + '</p>');
                setTimeout(function () {
                    connect(connectionAttempt + 1);
                }, 5000);
            } else {
                alert("The connection with the server was lost")
            }

        };

    };

    connect(1);

});