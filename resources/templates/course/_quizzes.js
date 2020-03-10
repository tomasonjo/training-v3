/* global $, Cookies, jwt_decode */
document.addEventListener('DOMContentLoaded', function () {

  var gradeQuizElement = $('[data-action="grade-quiz"]')
  var siteUrl = window.location.href

  var getTimeDiff = function (time1, time2) {
    var hourDiff = time2 - time1
    var diffDays = Math.floor(hourDiff / 86400000)
    var diffHrs = Math.floor((hourDiff % 86400000) / 3600000)
    var diffMins = Math.floor(((hourDiff % 86400000) % 3600000) / 60000)
    return {'days': diffDays, 'hours': diffHrs, 'mins': diffMins}
  }

  function arrayDiff(a, b) {
    return a.filter(function (i) {
      return b.indexOf(i) < 0
    })
  }

  function getQuizStatus() {
    var idToken = Cookies.get('com.neo4j.accounts.idToken')
    return $.ajax({
      type: 'GET',
      url: backendBaseUrl + '/getQuizStatus?className=' + trainingName,
      contentType: 'application/json',
      dataType: 'json',
      async: true,
      headers: {
        'Authorization': idToken
      }
    })
  }

  function setQuizStatus(passed, failed) {
    var idToken = Cookies.get('com.neo4j.accounts.idToken')
    var data = {
      'className': window.trainingClassName,
      'passed': passed,
      'failed': failed
    }
    return $.ajax({
      type: 'POST',
      url: backendBaseUrl + '/setQuizStatus',
      contentType: 'application/json',
      dataType: 'json',
      async: true,
      data: JSON.stringify(data),
      headers: {
        'Authorization': idToken
      }
    })
  }

  function getClassCertificate() {
    var idToken = Cookies.get('com.neo4j.accounts.idToken')
    return $.ajax({
      type: 'POST',
      url: backendBaseUrl + '/genClassCertificate',
      contentType: 'application/json',
      dataType: 'json',
      async: true,
      data: JSON.stringify({'className': trainingName}),
      headers: {
        'Authorization': idToken
      }
    })
  }

  function getEnrollmentForClass() {
    var idToken = Cookies.get('com.neo4j.accounts.idToken')
    return $.ajax({
      type: 'GET',
      url: backendBaseUrl + '/getClassEnrollment?className=' + trainingName,
      async: true,
      headers: {
        'Authorization': idToken
      }
    })
  }

  function enrollStudentInClass(firstName, lastName) {
    var idToken = Cookies.get('com.neo4j.accounts.idToken')
    return $.ajax({
      type: 'POST',
      url: backendBaseUrl + '/setClassEnrollment',
      contentType: 'application/json',
      dataType: 'json',
      async: true,
      data: JSON.stringify({
        'className': trainingName,
        'firstName': firstName,
        'lastName': lastName
      }),
      headers: {
        'Authorization': idToken
      }
    })
  }

  function logTrainingView() {
    var idToken = Cookies.get('com.neo4j.accounts.idToken')
    return $.ajax({
      type: 'POST',
      url: backendBaseUrl + '/logTrainingView',
      contentType: 'application/json',
      dataType: 'json',
      async: true,
      data: JSON.stringify({
        'className': trainingName,
        'partName': trainingPartName || 'unknown'
      }),
      headers: {
        'Authorization': idToken
      }
    })
  }

  function updatePageQuiz() {
    // && is (somehow) encoded as &#38;
    if (currentQuizStatus) {
      if (currentQuizStatus.passed) {
        if (currentQuizStatus.passed.indexOf(trainingPartName) !== -1) {
          var gradeQuizButton = gradeQuizElement.first()
          gradeQuizButton.hide()
          var gradeQuizElement = $('.quiz').first()
          gradeQuizElement.find('.required-answer').each(function () {
            $(this).prev(':checkbox').prop('checked', true)
          })
          gradeQuizElement.find('.false-answer').each(function () {
            $(this).prev(':checkbox').prop('checked', false)
          })
        }
      }
    }
  }

  function updateProgressIndicators(quizStatus) {
    document.querySelectorAll('[data-progress-indicator]').forEach(function (element) {
      var slug = element.dataset.slug
      // reset
      var classList = element.classList
      while (classList.length > 0) {
        classList.remove(classList.item(0))
      }
      var className
      if (quizStatus.failed.indexOf(slug) !== -1) {
        className = 'failed'
      } else if (quizStatus.passed.indexOf(slug) !== -1) {
        className = 'passed'
      } else if (quizStatus.untried && quizStatus.untried.indexOf(slug) !== -1) {
        className = 'untried'
      } else {
        className = 'none'
      }
      element.classList.add(className)
    })
  }

  function gradeQuiz(quizElement, slug) {
    if (currentQuizStatus && currentQuizStatus.passed && currentQuizStatus.passed.indexOf(slug) !== -1) {
      // already passed!
      return true
    }
    var quizSuccess = true
    // todo: we should use data attribute on the checkbox instead of span elements
    // todo: we should not define colors but class
    quizElement.find('h3').css('color', '#525865')
    quizElement.find('.required-answer').each(function () {
      if (!$(this).prev(':checkbox').prop('checked')) {
        $(this).closest('.ulist').siblings('h3').css('color', '#F44336');
        quizSuccess = false;
      }
    })
    quizElement.find('.false-answer').each(function () {
      if ($(this).prev(':checkbox').prop('checked')) {
        $(this).closest('.ulist').siblings('h3').css('color', '#F44336');
        quizSuccess = false;
      }
    })
    if (quizSuccess && currentQuizStatus && currentQuizStatus.passed) {
      currentQuizStatus.passed.push(slug)
    }
    return quizSuccess
  }

  var attemptRenewToken = function (silent, nextTimeout, nextTimeoutSilent) {
    console.log('attempting to renew token...')
    var iframe = document.createElement('iframe')
    iframe.style.display = 'none'
    iframe.src = 'https://neo4j.com/accounts/login?targetUrl=' + encodeURI(siteUrl)
    document.body.appendChild(iframe)
    if (nextTimeout) {
      setTimeout(function () {
        attemptRenewToken(nextTimeoutSilent, nextTimeout, nextTimeoutSilent);
      }, nextTimeout)
    }
  }

  var logout = function () {
    window.location = 'https://neo4j.com/accounts/login/?targetUrl=' + encodeURI(siteUrl)
  }

  function checkTokenExpiration(idToken) {
    var decodedToken = jwt_decode(idToken);
    var expiresIn = getTimeDiff(Date.now(), (decodedToken.exp) * 1000)
    if (expiresIn.days > 0 || expiresIn.hours > 0) {
      // token is good.
    } else if (expiresIn.days === 0 && expiresIn.hours === 0 && expiresIn.mins > 1 && expiresIn.mins < 60) {
      // expiring soon, let's immediately get a new token
      attemptRenewToken(true, 1000 * 60 * 30, false)
    } else {
      // token is already expired, log user out in UI; token won't work
      logout()
    }
  }

  // events

  gradeQuizElement.click(function (event) {
    event.preventDefault()

    var quizResultElement = $('#quiz-result')
    if (quizResultElement) {
      quizResultElement.remove()
    }

    var target = event.target
    var hrefSuccess = target.href
    var quizSuccess = gradeQuiz($('.quiz').first(), target.dataset.slug)
    if (quizSuccess) {
      $(target).before('<div id="quiz-result">' +
        '<p class="paragraph">' +
        '<span style="color: #63b345">All good!</span> you can advance to next section.' +
        '</p>' +
        '</div>'
      )
    } else {
      $(target).before('<div id="quiz-result">' +
        '<p class="paragraph">' +
        '<span style="color: #F44336">Please correct errors</span> in quiz responses above to continue. Questions with incorrect responses are highlighted in <span style="color: #F44336">red</span>.' +
        '<br>' +
        '<a href="' + hrefSuccess + '">Click here</a> if you wish to advance to next section without passing the quiz.' +
        '</p>' +
        '</div>'
      )
    }

    // update indicators
    updateProgressIndicators(currentQuizStatus)
    setQuizStatus(currentQuizStatus['passed'], currentQuizStatus['failed'])
      .catch(function (error) {
        // question: what should we do? display an error message to the user?
        console.error('Unable to update quiz status', error)
      })
  })

  // initial state
  var trainingName = window.trainingClassName
  var trainingModules = window.trainingClassModules
  var trainingPartName = window.trainingPartName
  var backendBaseUrl = 'https://nmae7t4ami.execute-api.us-east-1.amazonaws.com/prod'
  var currentQuizStatus = {
    failed: [],
    passed: [],
    untried: trainingModules
  }
  updateProgressIndicators(currentQuizStatus)

  var idToken = Cookies.get('com.neo4j.accounts.idToken');
  if (idToken) {
    // we're authenticated!
    // check if the token is not expired (or will expire soon)
    checkTokenExpiration(idToken)
    // get the current quiz status from the server
    getQuizStatus()
      .then(function (response) {
        var quizStatus = response['quizStatus']
        if (quizStatus) {
          var failed = quizStatus['failed']
          var passed = quizStatus['passed']
          var untried = arrayDiff(arrayDiff(trainingModules, failed), passed)
          currentQuizStatus = {
            failed: failed,
            passed: passed,
            untried: untried
          }
          updateProgressIndicators(currentQuizStatus)
          updatePageQuiz()
        } else {
          console.warn('Unable to update the current quiz status, response from the server is empty', response)
        }
      })
      .catch(function (error) {
        console.error('Unable to get quiz status', error)
      })
  } else if (typeof trainingPartName !== 'undefined') {
    logout()
  }
})
