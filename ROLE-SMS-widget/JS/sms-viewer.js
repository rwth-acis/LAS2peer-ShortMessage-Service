(function() {
  var api, fetchEntries, initEvents, iwcCallback, iwcManager, login, smsLib, requestSender, sendMessage;

  api = i5.las2peer.jsAPI;

  smsLib = i5.las2peer.sms;


  /*
    Check explicitly if gadget is known, i.e. script is executed in widget environment.
    Allows compatibility as a Web page.
   */

  if (typeof gadgets !== "undefined" && gadgets !== null) {
    iwcCallback = function(intent) {};
    iwcManager = new api.IWCManager(iwcCallback);
  }

  login = new api.Login(api.LoginTypes.HTTP_BASIC);

  login.setUserAndPassword(clientDefaults.userName, clientDefaults.userPassword);


  /*
    Init RequestSender object with uri and login data.
   */

  requestSender = new api.RequestSender(clientDefaults.address, login);

  $(document).ready(function() {
    initMessageSelector();
  });

  initMessageSelector = function() {
    initEvents();
    fetchEntries();
  };

  initEvents = function() {
    window.setInterval(function() {
      fetchEntries();
    }, 250);
    $("#refreshMessages").click(function() {
      fetchEntries();
    });
    $("#text").focus(function() {
      $("#hint").html("");
    });
    $("#text").on('input', function() {
      $("#hint").html("");
    });
    $("#send").click(function() {
      sendMessage();
    });
    $("#text").focus();
  };


  /*
    Request service for messages.
   */

  fetchEntries = function() {
//    $("#content").val("loading...");
    requestSender.sendRequest("get", "getShortMessagesAsString", "", function(data) {
        $("#content").val(data);
        console = document.getElementById("content");
        console.scrollTop = console.scrollHeight;
    }, function(error) {
        $("#content").val(error);
        alert(error);
    });
  };

  sendMessage = function() {
    agent = document.getElementById("agent");
    text = document.getElementById("text");
    requestSender.sendRequest("get", "sendShortMessage/"+agent.value+"/"+text.value, "", function(data) {
        $("#hint").html(data);
    }, function(error) {
        $("#hint").html(error);
        alert(error);
    });
    text.value = '';
    text.focus();
  };

}).call(this);