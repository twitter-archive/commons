from twitter.common.collections import OrderedDict, OrderedSet
from twitter.pants import is_internal
from twitter.pants.targets import InternalTarget
from twitter.pants.tasks import TaskError

class Group(object):
  @staticmethod
  def execute(phase, tasks_by_goal, context, executed, timer=None):
    """Executes the named phase against the current context tracking goal executions in executed."""

    def _acquire_lock_if_needed(goal):
      """If the goal about to be executed requires the lock, then acquire it. If not,
         then make sure it's released."""
      if goal.serialize:
        context.acquire_lock()
      else:
        context.release_lock()

    def execute_task(name, task, targets):
      """Execute and time a single goal that has had all of its dependencies satisfied."""
      start = timer.now() if timer else None
      try:
        task.execute(targets)
      finally:
        elapsed = timer.now() - start if timer else None
        if phase not in executed:
          executed[phase] = OrderedDict()
        if elapsed:
          phase_timings = executed[phase]
          if name not in phase_timings:
            phase_timings[name] = []
          phase_timings[name].append(elapsed)

    if phase not in executed:
      # Satisfy dependencies first
      goals = phase.goals()
      if not goals:
        raise TaskError('No goals installed for phase %s' % phase)

      for goal in goals:
        for dependency in goal.dependencies:
          Group.execute(dependency, tasks_by_goal, context, executed, timer=timer)

      runqueue = []
      goals_by_group = {}
      for goal in goals:
        if goal.group:
          group_name = goal.group.name
          if group_name not in goals_by_group:
            group_goals = [goal]
            runqueue.append((group_name, group_goals))
            goals_by_group[group_name] = group_goals
          else:
            goals_by_group[group_name].append(goal)
        else:
          runqueue.append((None, [goal]))
      try:
        for group_name, goals in runqueue:
          if not group_name:
            goal = goals[0]
            _acquire_lock_if_needed(goal)
            context.log.info('[%s:%s]' % (phase, goal.name))
            execute_task(goal.name, tasks_by_goal[goal], context.targets())
          else:
            for chunk in Group.create_chunks(context, goals):
              for goal in goals:
                _acquire_lock_if_needed(goal)
                goal_chunk = filter(goal.group.predicate, chunk)
                if len(goal_chunk) > 0:
                  context.log.info('[%s:%s:%s]' % (phase, group_name, goal.name))
                  execute_task(goal.name, tasks_by_goal[goal], goal_chunk)
      finally:
        # Once the goals are satisfied, make sure that the lock isn't still being held.
        context.release_lock()

  @staticmethod
  def create_chunks(context, goals):
    def discriminator(target):
      for i, goal in enumerate(goals):
        if goal.group.predicate(target):
          return i
      return 'other'

    # TODO(John Sirois): coalescing should be made available in another spot, InternalTarget is jvm
    # specific, and all we care is that the Targets have dependencies defined
    coalesced = InternalTarget.coalesce_targets(context.targets(is_internal), discriminator)
    coalesced = list(reversed(coalesced))

    def not_internal(target):
      return not is_internal(target)
    rest = OrderedSet(context.targets(not_internal))

    chunks = [rest] if rest else []
    flavor = None
    chunk_start = 0
    for i, target in enumerate(coalesced):
      target_flavor = discriminator(target)
      if target_flavor != flavor and i > chunk_start:
        chunks.append(OrderedSet(coalesced[chunk_start:i]))
        chunk_start = i
      flavor = target_flavor
    if chunk_start < len(coalesced):
      chunks.append(OrderedSet(coalesced[chunk_start:]))

    context.log.debug('::: created chunks(%d)' % len(chunks))
    for i, chunk in enumerate(chunks):
      context.log.debug('  chunk(%d):\n\t%s' % (i, '\n\t'.join(sorted(map(str, chunk)))))

    return chunks

  def __init__(self, name, predicate):
    self.name = name
    self.predicate = predicate
