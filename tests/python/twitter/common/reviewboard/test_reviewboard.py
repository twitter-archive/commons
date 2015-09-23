from twitter.common.reviewboard.reviewboard import ReviewBoardServer

import mock
from mock import call
import pytest

@mock.patch.object(ReviewBoardServer, "http_request", autospec=True, spec_set=True)
def test_fetch_review_requests(mock_method):
  mock_method.return_value = '''{
    "stat" : "ok",
    "review_requests" : [
      { "id" : 123 }, { "id" : 456 }
    ]
  }'''
  rbs = ReviewBoardServer('http://example.com', username='user', password='pass1')
  assert len(rbs.fetch_review_requests()) == 2
  assert mock_method.mock_calls == [
    call(rbs, "api/review-requests/?start=0&max-results=25",
         None, None, {'Accept': 'application/json'}, method=None)
  ]


@mock.patch.object(ReviewBoardServer, "http_request", autospec=True, spec_set=True)
def test_get_reviews(mock_method):
  mock_method.return_value = '''{
    "stat" : "ok",
    "reviews" : [
      { "id" : 123 }, { "id" : 456 }
    ]
  }'''
  rbs = ReviewBoardServer('http://example.com', username='user', password='pass1')
  rb_id = 12345
  assert len(rbs.get_reviews(rb_id)) == 2
  assert mock_method.mock_calls == [
    call(rbs, "api/review-requests/%d/reviews/?start=0&max-results=25" % rb_id,
         None, None, {'Accept': 'application/json'}, method=None)
  ]

@mock.patch.object(ReviewBoardServer, "http_request", autospec=True, spec_set=True)
def test_get_replies(mock_method):
  mock_method.return_value = '''{
    "stat" : "ok",
    "replies" : [
      { "id" : 123 }, { "id" : 456 }
    ]
  }'''
  rbs = ReviewBoardServer('http://example.com', username='user', password='pass1')
  rb_id = 123
  review_id = 456
  assert len(rbs.get_replies(rb_id, review_id)) == 2
  assert mock_method.mock_calls == [
    call(rbs, "api/review-requests/%d/reviews/%d/replies/?start=0&max-results=25" % (rb_id, review_id),
         None, None, {'Accept': 'application/json'}, method=None)
  ]

@mock.patch.object(ReviewBoardServer, "http_request", autospec=True, spec_set=True)
def test_get_changes(mock_method):
  mock_method.return_value = '''{
    "stat" : "ok",
    "changes" : [
      { "id" : 123 }, { "id" : 456 }
    ]
  }'''
  rbs = ReviewBoardServer('http://example.com', username='user', password='pass1')
  rb_id = 123
  assert len(rbs.get_changes(rb_id)) == 2
  assert mock_method.mock_calls == [
    call(rbs, "api/review-requests/%d/changes/?start=0&max-results=25" % rb_id,
         None, None, {'Accept': 'application/json'}, method=None)
  ]

@mock.patch.object(ReviewBoardServer, "http_request", autospec=True, spec_set=True)
def test_get_diffs(mock_method):
  mock_method.return_value = '''{
    "stat" : "ok",
    "diffs" : [
      { "id" : 123 }, { "id" : 456 }
    ]
  }'''
  rbs = ReviewBoardServer('http://example.com', username='user', password='pass1')
  rb_id = 123
  assert len(rbs.get_diffs(rb_id)) == 2
  assert mock_method.mock_calls == [
    call(rbs, "api/review-requests/%d/diffs/" % rb_id,
         None, None, {'Accept': 'application/json'}, method=None)
  ]

@mock.patch.object(ReviewBoardServer, "http_request", autospec=True, spec_set=True)
def test_get_files(mock_method):
  mock_method.return_value = '''{
    "stat" : "ok",
    "files" : [
      { "id" : 123 }, { "id" : 456 }
    ]
  }'''
  rbs = ReviewBoardServer('http://example.com', username='user', password='pass1')
  rb_id = 123
  revision = 456
  assert len(rbs.get_files(rb_id, revision)) == 2
  assert mock_method.mock_calls == [
    call(rbs, "api/review-requests/%d/diffs/%d/files/?start=0&max-results=25" % (rb_id, revision),
         None, None, {'Accept': 'application/json'}, method=None)
  ]

@mock.patch.object(ReviewBoardServer, "http_request", autospec=True, spec_set=True)
def test_diff_comments(mock_method):
  mock_method.return_value = '''{
    "stat" : "ok",
    "diff_comments" : [
      { "id" : 123 }, { "id" : 456 }
    ]
  }'''
  rbs = ReviewBoardServer('http://example.com', username='user', password='pass1')
  rb_id = 123
  revision = 456
  file_id = 789
  assert len(rbs.get_diff_comments(rb_id, revision, file_id)) == 2
  assert mock_method.mock_calls == [
    call(rbs, "api/review-requests/%d/diffs/%d/files/%d/diff-comments/?start=0&max-results=25" % (rb_id, revision, file_id),
         None, None, {'Accept': 'application/json'}, method=None)
  ]

def test_get_url():
  for url in ['http://reviewboard.com', 'http://reviewboard.com/']:
    assert ReviewBoardServer(url, username='user', password='pass1').get_url(123456) == 'http://reviewboard.com/r/123456'
