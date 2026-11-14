import requests
import json
import time

test_prompts = [
    ('en', 'who are you and what do you do'),
    ('en', 'tell me a joke about computers'),
    ('en', 'what is the square root of 144 plus 56'),
    ('en', 'how can a visually impaired person navigate a crowded subway station'),
    ('en', 'suggest three healthy high protein snacks'),
    ('en', 'explain the theory of relativity in simple words'),
    ('en', 'what is the capital of Australia and what is special about it'),
    ('hi', 'नमस्ते आप कौन हैं और आप क्या कर सकते हैं'),
    ('hi', 'मुझे एक नया और मजेदार चुटकुला सुनाओ'),
    ('hi', 'दृष्टिबाधित व्यक्ति के लिए स्वतंत्र रूप से चलने के 3 मुख्य सुझाव क्या हैं')
]

print("======================================================================")
print("  BLINDSIGHT ULTRA-FAST DYNAMIC AI TEST SUITE (10 END-TO-END TESTS)   ")
print("======================================================================\n")

all_passed = True
for idx, (lang, q) in enumerate(test_prompts, 1):
    t0 = time.time()
    try:
        r = requests.post(
            'http://127.0.0.1:5000/api/chat',
            json={'prompt': q, 'lang': lang},
            timeout=10
        )
        elapsed = time.time() - t0
        data = r.json()
        reply = data.get('reply', '')
        # Check that response is NOT the old canned fallback string
        canned_substring = "thoughtful question! I am right here walking with you"
        is_canned = canned_substring in reply
        passed = (r.status_code == 200) and (len(reply) > 20) and not is_canned

        if not passed:
            all_passed = False

        status_str = "PASS (Dynamic AI)" if passed else "FAIL (Canned or Empty)"
        print(f"[{idx:02d}/10] [{lang.upper()}] ({elapsed:.2f}s) Q: {q}")
        print(f"     A: {reply}")
        print(f"     Result: {status_str}\n")
    except Exception as e:
        print(f"[{idx:02d}/10] [{lang.upper()}] ERROR: {e}\n")
        all_passed = False

print("======================================================================")
if all_passed:
    print("  RESULT: ALL 10 TESTS PASSED! ZERO CANNED VOICELINES DETECTED.      ")
else:
    print("  RESULT: SOME TESTS FAILED!                                          ")
print("======================================================================")
