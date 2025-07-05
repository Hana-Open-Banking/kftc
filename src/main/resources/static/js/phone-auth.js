let sessionId = new URLSearchParams(window.location.search).get('sessionId');
let phoneNumber = '';

function formatPhoneNumber(value) {
    const numbers = value.replace(/[^\d]/g, '');
    if (numbers.length <= 3) return numbers;
    if (numbers.length <= 7) return numbers.slice(0, 3) + '-' + numbers.slice(3);
    return numbers.slice(0, 3) + '-' + numbers.slice(3, 7) + '-' + numbers.slice(7, 11);
}

document.getElementById('phoneNumber').addEventListener('input', function (e) {
    e.target.value = formatPhoneNumber(e.target.value);
});

function formatSocialSecurityNumber(value) {
    const numbers = value.replace(/[^\d]/g, '');
    if (numbers.length <= 6) return numbers;
    return numbers.slice(0, 6) + '-' + numbers.slice(6, 13);
}

document.getElementById('socialSecurityNumber').addEventListener('input', function (e) {
    e.target.value = formatSocialSecurityNumber(e.target.value);
});

async function sendVerificationCode() {
    const userName = document.getElementById('userName').value.trim();
    const userEmail = document.getElementById('userEmail').value.trim();
    const socialSecurityNumber = document.getElementById('socialSecurityNumber').value.replace(/[^\d]/g, '');
    phoneNumber = document.getElementById('phoneNumber').value.replace(/[^\d]/g, '');

    if (!userName) {
        alert('이름을 입력해주세요.');
        return;
    }

    if (!userEmail || !userEmail.includes('@')) {
        alert('올바른 이메일을 입력해주세요.');
        return;
    }

    if (socialSecurityNumber.length !== 13) {
        alert('올바른 주민등록번호를 입력해주세요. (13자리)');
        return;
    }

    if (phoneNumber.length !== 11) {
        alert('올바른 휴대폰 번호를 입력해주세요.');
        return;
    }

    try {
        const response = await fetch('/oauth/phone/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `session_id=${sessionId}&phone_number=${phoneNumber}&user_name=${encodeURIComponent(userName)}&user_email=${encodeURIComponent(userEmail)}&social_security_number=${socialSecurityNumber}`
        });

        const result = await response.json();
        if (response.ok) {
            alert('인증번호가 발송되었습니다.');
            document.getElementById('phone-step').style.display = 'none';
            document.getElementById('verification-step').style.display = 'block';
        } else {
            alert('인증번호 발송에 실패했습니다: ' + result.message);
        }
    } catch (error) {
        alert('네트워크 오류가 발생했습니다.');
    }
}

async function verifyCode() {
    const verificationCode = document.getElementById('verificationCode').value;

    if (verificationCode.length !== 6) {
        alert('6자리 인증번호를 입력해주세요.');
        return;
    }

    try {
        const response = await fetch('/oauth/phone/verify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `session_id=${sessionId}&phone_number=${phoneNumber}&verification_code=${verificationCode}`
        });

        if (response.ok) {
            document.body.innerHTML = await response.text();
        } else {
            const result = await response.text();
            document.body.innerHTML = result;
        }
    } catch (error) {
        alert('네트워크 오류가 발생했습니다.');
    }
}

function resendCode() {
    document.getElementById('phone-step').style.display = 'block';
    document.getElementById('verification-step').style.display = 'none';
}
