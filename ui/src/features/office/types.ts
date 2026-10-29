/** Shapes of the spaces API (F-04) as the office reads them. */
export type FloorSummary = {
  id: string;
  level: number;
  code: string;
  name: string;
  details?: string;
};

export type Building = {
  id: string;
  name: string;
  subtitle?: string;
  campusX: number;
  campusZ: number;
  floors: FloorSummary[];
};

export type Campus = { buildings: Building[] };

export type RoomKind =
  'OFFICE' | 'MEETING' | 'CONFERENCE' | 'HALL' | 'BOOTH' | 'SOCIAL' | 'LAB' | 'ZONE';

export type Room = {
  id: string;
  key: string;
  name: string;
  kind: RoomKind;
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
  isPrivate: boolean;
  capacity: number;
};

export type Furniture = Record<string, unknown>;

export type FloorLayout = {
  floorId: string;
  version: number;
  width: number;
  height: number;
  arrival: { x: number; y: number };
  tiles: string;
  furniture: Furniture;
  rooms: Room[];
};

export type OfficeView =
  | { mode: 'campus'; campus: Campus; layouts: FloorLayout[] }
  | { mode: 'building'; campus: Campus; building: Building; layouts: FloorLayout[] }
  | { mode: 'floor'; campus: Campus; building: Building; floor: FloorSummary; layout: FloorLayout };

export type Tools = { cutaway: boolean; overview: boolean; studio: boolean };

/** What the HUD can ask of the 3D view. */
export type OfficeControls = {
  rotate: (direction: 1 | -1) => void;
  zoomBy: (factor: number) => void;
  lookAt: (x: number, z: number) => void;
};

export type OfficeSceneProps = {
  view: OfficeView;
  tools: Tools;
  onReady: (controls: OfficeControls) => void;
  onOpenBuilding: (buildingId: string) => void;
  onOpenFloor: (floorId: string) => void;
  onStudioUnavailable: () => void;
};
