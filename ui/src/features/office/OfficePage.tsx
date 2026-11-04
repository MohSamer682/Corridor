import { lazy, Suspense, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { useSessionStore } from '../auth/sessionStore';
import {
  Breadcrumb,
  CampusCard,
  FloorRail,
  Hint,
  Minimap,
  Reconnecting,
  ViewTools,
  glass,
} from './OfficeHud';
import { usePresenceStore } from './presence/presenceStore';
import { usePresence } from './presence/usePresence';
import { useConversationStore } from '../talk/conversationStore';
import { CallPanel, ChatPanel } from '../talk/TalkPanels';
import { useTalkHint } from '../talk/useTalk';
import { MeetingPrompt } from '../meetings/MeetingPrompt';
import { usePeerConnections } from '../talk/usePeerConnections';
import { useLiveKitCall, type LiveKitJoin } from '../talk/useLiveKitCall';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/apiClient';
import type { Building, Campus, FloorSummary, OfficeControls, OfficeView, Tools } from './types';
import { useCampus, useFloorLayouts, useMyDesk, usePresenceCounts } from './useOffice';
import { hasWebGL } from './webgl';

const OfficeScene = lazy(() =>
  import('./OfficeScene').then((module) => ({ default: module.OfficeScene })),
);

type Target =
  | { mode: 'campus' }
  | { mode: 'building'; building: Building }
  | { mode: 'floor'; building: Building; floor: FloorSummary }
  | { mode: 'missing' };

function locate(campus: Campus, buildingId?: string, floorId?: string): Target {
  if (floorId) {
    for (const building of campus.buildings) {
      const floor = building.floors.find((f) => f.id === floorId);
      if (floor) return { mode: 'floor', building, floor };
    }
    return { mode: 'missing' };
  }
  if (buildingId) {
    const building = campus.buildings.find((b) => b.id === buildingId);
    return building ? { mode: 'building', building } : { mode: 'missing' };
  }
  return { mode: 'campus' };
}

function floorIdsFor(campus: Campus | undefined, target: Target | null): string[] {
  if (!campus || !target) return [];
  if (target.mode === 'campus')
    return campus.buildings.flatMap((b) => b.floors.slice(0, 1).map((f) => f.id));
  if (target.mode === 'building') return target.building.floors.map((f) => f.id);
  if (target.mode === 'floor') return [target.floor.id];
  return [];
}

export function OfficePage() {
  const { buildingId, floorId } = useParams();
  const navigate = useNavigate();
  const campus = useCampus();
  const webgl = useMemo(() => hasWebGL(), []);
  const [tools, setTools] = useState<Tools>({ cutaway: false, overview: false, studio: true });
  const [studioAvailable, setStudioAvailable] = useState(true);
  const [controls, setControls] = useState<OfficeControls | null>(null);
  const target = campus.data ? locate(campus.data, buildingId, floorId) : null;
  const layouts = useFloorLayouts(floorIdsFor(campus.data, target));
  const meId = useSessionStore((state) => state.user?.id ?? null);
  const counts = usePresenceCounts().data ?? {};
  const desk = useMyDesk();
  const presence = usePresence(target?.mode === 'floor' ? target.floor.id : null);
  const connected = usePresenceStore((state) => state.connected);
  const everConnected = usePresenceStore((state) => state.version > 0);
  const alone = usePresenceStore(
    (state) => state.version > 0 && Object.keys(state.people).length === 1,
  );
  const amHere = usePresenceStore((state) => (meId ? Boolean(state.people[meId]) : false));
  const group = useConversationStore((state) => state.group);
  const inFocus = useConversationStore((state) => (meId ? state.focused.includes(meId) : false));
  const peerCall = usePeerConnections(group, meId, presence.send);
  // Five or more people talk through LiveKit; the ticket is tied to the group.
  const sfuTicket = useQuery({
    queryKey: ['conversation-media', group?.groupId],
    queryFn: () => apiClient<LiveKitJoin>('/api/v1/conversations/media'),
    enabled: group?.route === 'SFU',
    staleTime: Infinity,
  });
  const sfuCall = useLiveKitCall(group?.route === 'SFU' ? (sfuTicket.data ?? null) : null);
  const call = group?.route === 'SFU' ? sfuCall : peerCall;
  const talkHint = useTalkHint(meId);
  // "Go to my desk" opens the floor, then walks there once you have arrived.
  const location = useLocation();
  const walkTo = (location.state as { walkTo?: { x: number; y: number } } | null)?.walkTo;
  useEffect(() => {
    if (!walkTo || !amHere) return;
    presence.walk(walkTo.x, walkTo.y);
    navigate(location.pathname, { replace: true, state: null });
  }, [walkTo, amHere, presence, navigate, location.pathname]);

  const view = useMemo<OfficeView | null>(() => {
    if (!campus.data || !target || layouts.isPending || layouts.isError) return null;
    if (target.mode === 'campus')
      return { mode: 'campus', campus: campus.data, layouts: layouts.layouts };
    if (target.mode === 'building')
      return {
        mode: 'building',
        campus: campus.data,
        building: target.building,
        layouts: layouts.layouts,
      };
    if (target.mode === 'floor' && layouts.layouts[0])
      return {
        mode: 'floor',
        campus: campus.data,
        building: target.building,
        floor: target.floor,
        layout: layouts.layouts[0],
      };
    return null;
  }, [campus.data, target, layouts.isPending, layouts.isError, layouts.layouts]);

  const openBuilding = (id: string) => navigate(`/office/buildings/${id}`);
  const openFloor = (id: string) => navigate(`/office/floors/${id}`);
  const goToMyDesk = () => {
    if (desk.data)
      navigate(`/office/floors/${desk.data.floorId}`, {
        state: { walkTo: { x: desk.data.x, y: desk.data.y } },
      });
  };
  const retry = () => (campus.isError ? campus.refetch() : layouts.refetch());

  let content: ReactNode;
  if (campus.isError || layouts.isError) content = <LoadError onRetry={retry} />;
  else if (campus.data && campus.data.buildings.length === 0)
    content = (
      <Notice title="Your office isn't set up yet.">
        Once your company's buildings are added, they show up here.
      </Notice>
    );
  else if (target?.mode === 'missing') content = <Notice title="This floor isn't open yet." />;
  else if (!webgl)
    content = (
      <Notice title="The 3D office needs WebGL, which this browser has turned off.">
        Turn on hardware acceleration in your browser settings, then reload. The rest of Corridor
        still works.
      </Notice>
    );
  else if (!view || !campus.data || !target) content = <Loading />;
  else
    content = (
      <>
        <Suspense fallback={<Loading />}>
          <OfficeScene
            view={view}
            tools={tools}
            onReady={setControls}
            onOpenBuilding={openBuilding}
            onOpenFloor={openFloor}
            onWalk={presence.walk}
            onStep={presence.step}
            meId={meId}
            onStudioUnavailable={() => {
              setStudioAvailable(false);
              setTools((current) => ({ ...current, studio: false }));
            }}
          />
        </Suspense>
        <Breadcrumb
          building={'building' in target ? target.building : undefined}
          floor={target.mode === 'floor' ? target.floor : undefined}
          onCampus={() => navigate('/office')}
          onBuilding={openBuilding}
        />
        <ViewTools
          mode={target.mode}
          tools={tools}
          studioAvailable={studioAvailable}
          controls={controls}
          onChange={setTools}
          focus={{
            on: inFocus,
            toggle: () => presence.send('/app/conversations/focus', { on: !inFocus }),
          }}
        />
        {target.mode === 'campus' && (
          <CampusCard
            campus={campus.data}
            counts={counts}
            hasDesk={desk.isPending ? undefined : Boolean(desk.data)}
            onEnter={openBuilding}
            onMyDesk={goToMyDesk}
          />
        )}
        {target.mode !== 'campus' && (
          <FloorRail
            campus={campus.data}
            building={target.building}
            currentFloorId={target.mode === 'floor' ? target.floor.id : undefined}
            counts={counts}
          />
        )}
        {view.mode === 'floor' && meId && (
          <>
            <ChatPanel
              me={meId}
              onSend={(body) => presence.send('/app/conversations/messages', { body })}
            />
            <CallPanel call={call} me={meId} />
          </>
        )}
        {view.mode === 'floor' && meId && <MeetingPrompt me={meId} rooms={view.layout.rooms} />}
        {view.mode === 'floor' && !group && (
          <Minimap layout={view.layout} level={view.floor.level} controls={controls} />
        )}
        {target.mode === 'floor' && everConnected && !connected && <Reconnecting />}
        {target.mode === 'floor' && connected && alone && !talkHint && (
          <Hint>You're the first one on this floor.</Hint>
        )}
        {target.mode === 'floor' && connected && talkHint && !group && <Hint>{talkHint}</Hint>}
        {target.mode !== 'floor' && (
          <Hint>
            {target.mode === 'campus'
              ? 'Click a building to look inside'
              : 'Click a floor to go there'}
          </Hint>
        )}
      </>
    );

  return (
    <div className="mx-auto box-border w-full max-w-[1520px] px-4 py-4">
      <div className="relative h-[clamp(480px,76vh,820px)] touch-none overflow-hidden rounded-[18px] bg-[linear-gradient(#C3D3E3,#E9EEF2_60%,#EEF1F3)] text-[#141B2D] shadow-[0_24px_60px_rgba(20,27,45,.18)] select-none">
        {content}
      </div>
    </div>
  );
}

function Centered({ children }: { children: ReactNode }) {
  return (
    <div className="absolute inset-0 grid place-items-center p-6 text-center">
      <div className={`${glass} flex max-w-[420px] flex-col items-center gap-2.5 p-7`}>
        {children}
      </div>
    </div>
  );
}

function Loading() {
  return (
    <Centered>
      <div role="status" className="flex flex-col items-center gap-2.5">
        <span
          aria-hidden="true"
          className="size-7 animate-spin rounded-full border-[3px] border-[#D5DBE4] border-t-[#23395D] motion-reduce:animate-none"
        />
        <b>Setting up the office…</b>
        <span className="text-sm text-[#5B6478]">The 3D view loads once, then stays fast.</span>
      </div>
    </Centered>
  );
}

function LoadError({ onRetry }: { onRetry: () => void }) {
  return (
    <Centered>
      <div role="alert" className="flex flex-col items-center gap-2.5">
        <b className="text-[#8E2F25]">We couldn't load the office.</b>
        <span className="text-sm text-[#5B6478]">Check your connection and try again.</span>
        <button
          type="button"
          onClick={onRetry}
          className="h-11 cursor-pointer rounded-[10px] border border-[#D5DBE4] bg-white px-[18px] font-semibold text-[#141B2D]"
        >
          Try again
        </button>
      </div>
    </Centered>
  );
}

function Notice({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <Centered>
      <b>{title}</b>
      {children && <span className="text-sm text-[#5B6478]">{children}</span>}
    </Centered>
  );
}
