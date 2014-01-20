import textwrap

from twitter.common.jira import Jira, JiraError

import mock
import pytest

GOOD_TRANSITIONS = textwrap.dedent('''{
                                        "transitions": [
                                          {
                                            "id": 1,
                                            "name": "Test"
                                          },
                                          {
                                            "id": 2,
                                            "name": "Resolve"
                                          }
                                        ]
                                      }''')

BAD_TRANSITIONS = textwrap.dedent('''{
                                        "transitions": [
                                          {
                                            "id": 1,
                                            "name": "Test"
                                          },
                                          {
                                            "id": 2,
                                            "name": "Fail"
                                          }
                                        ]
                                      }''')


@mock.patch("twitter.common.jira.jira.Jira.get_transitions")
def test_get_resolve_transition_id(mock_transitions):
  mock_transitions.return_value = GOOD_TRANSITIONS
  jira = Jira("test")
  assert jira._get_resolve_transition_id('test-7') == 2


@mock.patch("twitter.common.jira.jira.Jira.get_transitions")
def test_get_resolve_transition_id_error(mock_transitions):
  mock_transitions.return_value = BAD_TRANSITIONS
  jira = Jira("test")
  with pytest.raises(JiraError):
    jira._get_resolve_transition_id('test-7')
